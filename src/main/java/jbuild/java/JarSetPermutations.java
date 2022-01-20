package jbuild.java;

import jbuild.errors.JBuildException;
import jbuild.log.JBuildLog;
import jbuild.util.Either;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static jbuild.errors.JBuildException.ErrorCause.UNKNOWN;
import static jbuild.util.AsyncUtils.awaitValues;
import static jbuild.util.CollectionUtils.mapValues;

/**
 * Utility class to find valid permutations of jars, given a set of entry-point jars and a larger set of available jars.
 * <p>
 * This class only takes into consideration the types available in each jar.
 *
 * @see ClassGraph
 */
public final class JarSetPermutations {

    private final JBuildLog log;

    public JarSetPermutations(JBuildLog log) {
        this.log = log;
    }

    public static JarSetPermutations create(JBuildLog log) {
        return new JarSetPermutations(log);
    }

    /**
     * Compute the unique permutations of jars by which all types will belong to a single jar only,
     * returning a list of {@link JarSet}s which will not have any type conflicts.
     *
     * @param jarFiles to consider
     * @return all permutations of {@link JarSet} that are possible without internal conflicts
     */
    public CompletionStage<List<JarSet>> fromJars(File... jarFiles) {
        var service = Executors.newFixedThreadPool(
                Math.max(4, Runtime.getRuntime().availableProcessors()),
                new JarLoaderThreadFactory());

        var jarLoader = new Jar.Loader(log, service);

        var jarsCompletion = awaitValues(Arrays.stream(jarFiles)
                .map(jarLoader::lazyLoad)
                .collect(toList()));

        return jarsCompletion.thenApplyAsync((ok) -> {
            var jarsByType = computeJarsByType(ok);
            return compute(jarsByType);
        }, service);
    }

    /**
     * Compute the unique permutations of jars by which all types will belong to a single jar only,
     * returning a list of {@link JarSet}s which will not have any type conflicts.
     *
     * @param jarsByType map from type to the jars in which they can be found
     * @return all permutations of {@link JarSet} that are possible without internal conflicts
     */
    public List<JarSet> compute(Map<String, Set<File>> jarsByType) {
        var forbiddenJarsByJar = new HashMap<File, Set<File>>();
        var typesByJar = new HashMap<File, Set<String>>();

        jarsByType.forEach((type, jars) -> {
            for (var jar : jars) {
                typesByJar.computeIfAbsent(jar, (ignore) -> new HashSet<>(32)).add(type);
                var forbidden = forbiddenJarsByJar.computeIfAbsent(jar, (ignore) -> new HashSet<>(2));
                for (var jar2 : jars) {
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

        return compute(jarPermutations, typesByJar);
    }

    private Map<String, Set<File>> computeJarsByType(Collection<Either<Jar, Throwable>> jarsOrErrors) {
        var errors = jarsOrErrors.stream()
                .map(it -> it.map(jar -> null, err -> err))
                .filter(Objects::nonNull)
                .collect(toList());

        if (!errors.isEmpty()) {
            throw new JBuildException("could not obtain jar contents: " + errors, UNKNOWN);
        }

        var jarsByType = new HashMap<String, Set<File>>(128);
        for (var item : jarsOrErrors) {
            item.use(jar -> {
                        for (var type : jar.types) {
                            jarsByType.computeIfAbsent(type, ignore -> new HashSet<>())
                                    .add(jar.file);
                        }
                    },
                    (ignore) -> {
                    });
        }
        return jarsByType;
    }

    private List<JarSet> compute(
            List<? extends Collection<File>> jarPermutations,
            Map<File, Set<String>> typesByJar) {
        return jarPermutations.stream()
                .map(jars -> {
                    var jarByType = new HashMap<String, File>();
                    var typeByJar = new HashMap<File, Set<String>>();
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

    private void logJars(String header, boolean verbose, List<? extends Collection<File>> jarSets) {
        if (verbose && log.isVerbose()) {
            log.verbosePrintln(header);
            for (var jars : jarSets) {
                log.verbosePrintln(jars.stream()
                        .map(File::getName)
                        .collect(Collectors.joining(", ", "  * ", "")));
            }
        }
        if (!verbose) {
            log.println(header);
            for (var jars : jarSets) {
                log.println(jars.stream()
                        .map(File::getName)
                        .collect(Collectors.joining(", ", "  * ", "")));
            }
        }
    }

    private static List<? extends Set<File>> computePermutations(Set<File> okJars,
                                                                 List<? extends Collection<File>> duplicates) {
        var result = new ArrayList<Set<File>>();

        // the number of permutations is equal to the multiplication of the sizes of each list
        var permCount = duplicates.stream().mapToInt(Collection::size).reduce(1, (a, b) -> a * b);

        var dups = duplicates.stream().map(ArrayList::new).collect(toList());
        for (var i = 0; i < permCount; i++) {
            var permutation = new HashSet<File>(okJars.size() + duplicates.size());
            permutation.addAll(okJars);
            for (var dup : dups) {
                permutation.add(dup.get(i % dup.size()));
            }
            result.add(permutation);
        }
        assert new HashSet<>(result).size() == result.size() :
                "should be " + new HashSet<>(result) + " but was " + result;
        return result;
    }

    private static List<? extends Set<File>> flattenDuplicates(Map<File, Set<File>> dups) {
        var result = new ArrayList<Set<File>>();
        dups.forEach((jar, forbidden) -> {
            // if any set in result already contains any of the jars in this iteration, re-use that set
            var optSet = result.stream()
                    .filter(it -> it.contains(jar) || forbidden.stream().anyMatch(it::contains))
                    .findAny();
            Set<File> set;
            if (optSet.isPresent()) {
                set = optSet.get();
            } else {
                // keep jars sorted in reverse alphabetical order hoping that translates to newer jars first
                set = new TreeSet<>(comparing(File::getName).reversed());
                result.add(set);
            }
            set.add(jar);
            set.addAll(forbidden);
        });
        return result;
    }

    private static final class JarLoaderThreadFactory implements ThreadFactory {
        private final AtomicInteger count = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable runnable) {
            var thread = new Thread(runnable, "jar-loader-" + count.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
