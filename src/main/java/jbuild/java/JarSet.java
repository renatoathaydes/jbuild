package jbuild.java;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * A set of jars and their respective types.
 * <p>
 * To create a number of consistent (i.e. all types are unique within each jar)
 * {@link JarSet} instances from a given (possibly invalid) classpath,
 * a {@link JarSetPermutations} can be used.
 * <p>
 * Unlike in most other JBuild classes, the types names in this class use the Java standard syntax
 * instead of JVM internal type descriptors.
 */
public final class JarSet {

    private final Map<String, File> jarByType;
    private final Map<File, Set<String>> typesByJar;

    public JarSet(Map<String, File> jarByType,
                  Map<File, Set<String>> typesByJar) {
        this.jarByType = jarByType;
        this.typesByJar = typesByJar;
    }

    public JarSet(Map<String, File> jarByType) {
        this(jarByType, computeTypesByJar(jarByType));
    }

    public Map<String, File> getJarByType() {
        return jarByType;
    }

    public Map<File, Set<String>> getTypesByJar() {
        return typesByJar;
    }

    public Set<File> getJars() {
        return typesByJar.keySet();
    }

    public Set<String> getJarPaths() {
        return typesByJar.keySet().stream()
                .map(File::getPath)
                .collect(toSet());
    }

    public boolean containsAll(Set<File> jars) {
        return getJars().containsAll(jars);
    }

    public boolean containsAny(Set<Map.Entry<File, File>> jarPairs) {
        var jars = getJars();
        for (var pair : jarPairs) {
            if (jars.contains(pair.getKey()) && jars.contains(pair.getValue())) {
                return true;
            }
        }
        return false;
    }

    public String toClasspath() {
        return String.join(File.pathSeparator, new HashSet<>(getJarPaths()));
    }

    private static Map<File, Set<String>> computeTypesByJar(Map<String, File> jarByType) {
        var result = new HashMap<File, Set<String>>();
        jarByType.forEach((type, jar) -> result.computeIfAbsent(jar, (ignore) -> new HashSet<>(32))
                .add(type));
        return result;
    }

}
