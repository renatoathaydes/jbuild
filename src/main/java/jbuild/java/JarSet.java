package jbuild.java;

import jbuild.log.JBuildLog;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static jbuild.util.CollectionUtils.mapValues;

/**
 * A set of jars and their respective types.
 * <p>
 * To create a number of consistent (i.e. all types are unique within each jar)
 * {@link JarSet} instances from a given (possibly invalid) classpath, a {@link JarSet.Loader} can be used.
 * <p>
 * Unlike in most other JBuild classes, the types names in this class use the Java standard syntax
 * instead of JVM internal type descriptors.
 */
public final class JarSet {

    private final Map<String, String> jarByType;
    private final Map<String, Set<String>> typesByJar;

    public JarSet(Map<String, String> jarByType,
                  Map<String, Set<String>> typesByJar) {
        this.jarByType = jarByType;
        this.typesByJar = typesByJar;
    }

    public Map<String, String> getJarByType() {
        return jarByType;
    }

    public Map<String, Set<String>> getTypesByJar() {
        return typesByJar;
    }

    public Set<String> getJars() {
        return typesByJar.keySet();
    }

    public boolean containsAll(Set<String> jars) {
        return getJars().containsAll(jars);
    }

    public String toClasspath() {
        return String.join(File.pathSeparator, new HashSet<>(getJars()));
    }

    /**
     * Loader of consistent instances of {@link JarSet}.
     *
     * @see JarSet
     */
    public static final class Loader {

        private final JBuildLog log;

        public Loader(JBuildLog log) {
            this.log = log;
        }

        /**
         * Compute the unique permutations of jars by which all types will belong to a single jar only,
         * returning a list of {@link JarSet}s which will not have any type conflicts.
         *
         * @param jarsByType map from type to the jars in which they can be found
         * @return all permutations of {@link JarSet} that are possible without internal conflicts
         */
        public List<JarSet> computeUniqueJarSetPermutations(Map<String, Set<String>> jarsByType) {
            var forbiddenJarsByJar = new HashMap<String, Set<String>>();
            var typesByJar = new HashMap<String, Set<String>>();

            jarsByType.forEach((type, jars) -> {
                for (var jar : jars) {
                    typesByJar.computeIfAbsent(jar, (ignore) -> new HashSet<>(32)).add(type);
                    var forbidden = forbiddenJarsByJar.computeIfAbsent(jar, (ignore) -> new HashSet<>(2));
                    for (var jar2 : jars) {
                        //noinspection StringEquality
                        if (jar != jar2) forbidden.add(jar2);
                    }
                }
            });

            var partitions = forbiddenJarsByJar.entrySet().stream()
                    .collect(groupingBy(e -> e.getValue().isEmpty(),
                            mapping(e -> e, toMap(Map.Entry::getKey, Map.Entry::getValue))));

            var dups = partitions.getOrDefault(false, Map.of());
            if (dups.isEmpty()) {
                log.verbosePrintln("No conflicts were found between any jar");
                var jarByType = mapValues(jarsByType, jars -> jars.iterator().next());
                return List.of(new JarSet(jarByType, typesByJar));
            }

            var duplicates = flattenDuplicates(dups);
            logJars("The following jars conflict:", false, duplicates);
            var ok = partitions.getOrDefault(true, Map.of()).keySet();
            var jarPermutations = computePermutations(ok, duplicates);
            logJars("Jar permutations:", true, jarPermutations);

            return computeUniqueJarSetPermutations(jarPermutations, typesByJar);
        }

        private List<JarSet> computeUniqueJarSetPermutations(List<? extends Collection<String>> jarPermutations,
                                                             Map<String, Set<String>> typesByJar) {
            return jarPermutations.stream()
                    .map(jars -> {
                        var jarByType = new HashMap<String, String>();
                        var typeByJar = new HashMap<String, Set<String>>();
                        for (var jar : jars) {
                            var types = typesByJar.get(jar);
                            typeByJar.put(jar, types);
                            for (var type : types) {
                                var old = jarByType.put(type, jar);
                                if (old != null) {
                                    throw new RuntimeException("map already contains entry for type " + type +
                                            ": " + old + " (expected " + jar + ")");
                                }
                            }
                        }
                        return new JarSet(jarByType, typeByJar);
                    }).collect(toList());
        }

        private void logJars(String header, boolean verbose, List<? extends Collection<String>> jarSets) {
            if (verbose && log.isVerbose()) {
                log.verbosePrintln(header);
                for (var jars : jarSets) {
                    log.verbosePrintln(jars.stream()
                            .collect(Collectors.joining(", ", "  * ", "")));
                }
            }
            if (!verbose) {
                log.println(header);
                for (var jars : jarSets) {
                    log.println(jars.stream()
                            .collect(Collectors.joining(", ", "  * ", "")));
                }
            }
        }

        private static List<? extends Set<String>> computePermutations(Set<String> okJars,
                                                                       List<? extends Collection<String>> duplicates) {
            var result = new ArrayList<Set<String>>();

            // the number of permutations is equal to the multiplication of the sizes of each list
            var permCount = duplicates.stream().mapToInt(Collection::size).reduce(1, (a, b) -> a * b);

            var dups = duplicates.stream().map(ArrayList::new).collect(toList());
            for (var i = 0; i < permCount; i++) {
                var permutation = new HashSet<String>(okJars.size() + duplicates.size());
                permutation.addAll(okJars);
                for (var dup : dups) {
                    permutation.add(dup.get(i % dup.size()));
                }
                result.add(permutation);
            }
            assert new HashSet<>(result).size() == result.size();
            return result;
        }

        private static List<? extends Set<String>> flattenDuplicates(Map<String, Set<String>> dups) {
            var result = new ArrayList<Set<String>>();
            dups.forEach((jar, forbidden) -> {
                var optSet = result.stream().filter(it -> it.contains(jar)).findAny();
                Set<String> set;
                if (optSet.isPresent()) {
                    set = optSet.get();
                } else {
                    // keep jars sorted in reverse alphabetical order hoping that translates to newer jars first
                    set = new TreeSet<>(reverseOrder());
                    set.add(jar);
                    result.add(set);
                }
                set.addAll(forbidden);
            });
            return result;
        }
    }

}
