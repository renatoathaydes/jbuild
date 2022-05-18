package jbuild.java;

import jbuild.util.Either;
import jbuild.util.NonEmptyCollection;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static jbuild.util.AsyncUtils.awaitSuccessValues;
import static jbuild.util.CollectionUtils.filterValues;
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

    public JarSet filterFiles(Set<File> jarFiles) {
        var jars = getJars(jarFiles);
        if (jars.size() < jarFiles.size()) throw new IllegalArgumentException("not all jars exist in this Set");
        return filter(jars);
    }

    public JarSet filter(Set<Jar> jars) {
        if (jars.size() == jarFiles.size()) return this;
        return new JarSet(filterValues(jarByType, jars::contains));
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

    /**
     * Check if all the given {@link TypeReference}s exist in this {@link JarSet}.
     * <p>
     * If any type requirement is missing, the missing type references are returned.
     *
     * @param typeReferences type requirements
     * @return this JarSet if all type requirements are met, or the missing type references otherwise.
     */
    public Either<JarSet, NonEmptyCollection<TypeReference>> checkReferencesExist(
            List<TypeReference> typeReferences) {
        if (typeReferences.isEmpty()) return Either.left(this);
        var missingTypeRefs = new HashSet<TypeReference>();
        for (var typeRef : typeReferences) {
            var found = false;
            for (var types : typesByJar.values()) {
                found = types.contains(typeRef.typeTo);
                if (found) break;
            }
            if (!found) {
                missingTypeRefs.add(typeRef);
            }
        }
        if (missingTypeRefs.isEmpty()) {
            return Either.left(this);
        }
        return Either.right(NonEmptyCollection.of(missingTypeRefs));
    }

    public String toClasspath() {
        return String.join(File.pathSeparator, new HashSet<>(getJarPaths()));
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
                parsedJars.stream().collect(toMap(e -> e.file, e -> e.typeByName)),
                mapValues(jarByType, jar -> jar.file)));
    }

    @Override
    public String toString() {
        return "JarSet{" +
                "typesByJar=" + typesByJar +
                '}';
    }
}
