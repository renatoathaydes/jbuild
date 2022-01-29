package jbuild.java;

import jbuild.util.Either;
import jbuild.util.NonEmptyCollection;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;
import static jbuild.util.AsyncUtils.awaitSuccessValues;
import static jbuild.util.CollectionUtils.mapValues;

/**
 * A set of {@link Jar} and their respective types.
 * <p>
 * To create a number of consistent (i.e. all types are unique within each jar)
 * {@link JarSet} instances from a given (possibly invalid) classpath,
 * a {@link JarSetPermutations} can be used.
 */
public final class JarSet {

    private final Map<String, Jar> jarByType;
    private final Map<Jar, Set<String>> typesByJar;
    private final Set<File> jarFiles;

    public JarSet(Map<String, Jar> jarByType,
                  Map<Jar, Set<String>> typesByJar) {
        this.jarByType = jarByType;
        this.typesByJar = typesByJar;
        this.jarFiles = typesByJar.keySet().stream()
                .map(jar -> jar.file)
                .collect(toSet());
    }

    public JarSet(Map<String, Jar> jarByType) {
        this(jarByType, computeTypesByJar(jarByType));
    }

    public Map<String, Jar> getJarByType() {
        return jarByType;
    }

    public Map<Jar, Set<String>> getTypesByJar() {
        return typesByJar;
    }

    public Set<Jar> getJars() {
        return typesByJar.keySet();
    }

    /**
     * @param jarFiles to keep
     * @return the jars whose files are included in the given Set
     */
    public Set<Jar> getJars(Set<File> jarFiles) {
        return getJars().stream()
                .filter(jar -> jarFiles.contains(jar.file))
                .collect(toSet());
    }

    public Set<File> getJarFiles() {
        return jarFiles;
    }

    public Set<String> getJarPaths() {
        return typesByJar.keySet().stream()
                .map(jar -> jar.file.getPath())
                .collect(toSet());
    }

    public boolean containsAll(Set<File> jars) {
        return jarFiles.containsAll(jars);
    }

    public boolean containsAny(Set<Map.Entry<File, File>> jarPairs) {
        for (var pair : jarPairs) {
            if (jarFiles.contains(pair.getKey()) && jarFiles.contains(pair.getValue())) {
                return true;
            }
        }
        return false;
    }

    public Either<JarSet, NonEmptyCollection<String>> filter(
            Set<Jar> entryJars,
            Set<String> typeRequirements) {
        var errors = new HashSet<String>();
        var jarsToKeep = new HashSet<Jar>(jarFiles.size());
        jarsToKeep.addAll(entryJars);
        for (var typeRequirement : typeRequirements) {
            var found = false;
            for (var entry : typesByJar.entrySet()) {
                var jar = entry.getKey();
                var types = entry.getValue();
                found = types.contains(typeRequirement);
                if (found) {
                    jarsToKeep.add(jar);
                    break;
                }
            }
            if (!found) {
                errors.add("Type '" + typeRequirement + "', required by an entry-point, cannot be found in classpath");
            }
        }
        if (errors.isEmpty()) {
            if (jarsToKeep.size() == typesByJar.size()) {
                return Either.left(this); // keep all jars
            }
            return Either.left(createJarSet(jarsToKeep));
        }
        return Either.right(NonEmptyCollection.of(errors));
    }

    public String toClasspath() {
        return String.join(File.pathSeparator, new HashSet<>(getJarPaths()));
    }

    private static JarSet createJarSet(HashSet<Jar> jars) {
        Map<String, Jar> jarByType = new HashMap<>();
        Map<Jar, Set<String>> typesByJar = new HashMap<>(jars.size());

        for (var jar : jars) {
            typesByJar.put(jar, jar.types);
            for (var type : jar.types) {
                jarByType.put(type, jar);
            }
        }
        return new JarSet(jarByType, typesByJar);
    }

    private static Map<Jar, Set<String>> computeTypesByJar(Map<String, Jar> jarByType) {
        var result = new HashMap<Jar, Set<String>>();
        jarByType.forEach((type, jar) -> result.computeIfAbsent(jar, (ignore) -> new HashSet<>(32))
                .add(type));
        return result;
    }

    public CompletionStage<ClassGraph> toClassGraph() {
        var completions = new ArrayList<CompletionStage<Jar.ParsedJar>>(jarFiles.size());
        for (var jar : getJars()) {
            completions.add(jar.parsed());
        }
        return awaitSuccessValues(completions).thenApplyAsync((parsedJars) -> new ClassGraph(
                parsedJars.stream().collect(Collectors.toMap(e -> e.file, e -> e.typeByName)),
                mapValues(jarByType, jar -> jar.file)));
    }
}
