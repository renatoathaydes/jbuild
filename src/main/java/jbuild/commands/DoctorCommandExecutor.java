package jbuild.commands;

import jbuild.api.JBuildException;
import jbuild.java.JarSet;
import jbuild.java.JarSetPermutations;
import jbuild.log.JBuildLog;
import jbuild.util.CollectionUtils;
import jbuild.util.JarFileFilter;
import jbuild.util.NonEmptyCollection;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static jbuild.api.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.api.JBuildException.ErrorCause.USER_INPUT;
import static jbuild.util.AsyncUtils.awaitValues;
import static jbuild.util.FileUtils.allFilesInDir;
import static jbuild.util.TextUtils.LINE_END;

public final class DoctorCommandExecutor {

    private final JBuildLog log;
    private final JarSetPermutations jarSetPermutations;

    public DoctorCommandExecutor(JBuildLog log) {
        this(log, JarSetPermutations.create(log));
    }

    public DoctorCommandExecutor(JBuildLog log,
                                 JarSetPermutations jarSetPermutations) {
        this.log = log;
        this.jarSetPermutations = jarSetPermutations;
    }

    public CompletionStage<?> run(String inputDir,
                                  List<String> entryPoints,
                                  Set<Pattern> typeExclusions) {
        var results = findValidClasspaths(
                new File(inputDir),
                entryPoints.stream().map(File::new).collect(toList()),
                typeExclusions);
        return results.thenApply(this::showClasspathCheckResults);
    }

    public CompletionStage<? extends Collection<ClasspathCheckResult>> findValidClasspaths(
            File inputDir,
            List<File> entryPoints,
            Set<Pattern> typeExclusions) {
        var jarFiles = allFilesInDir(inputDir, JarFileFilter.getInstance());
        log.verbosePrintln(() -> "All provided jars: " + Arrays.toString(jarFiles));

        var entryJars = Stream.of(jarFiles)
                .map(jar -> entryPoints.stream()
                        .map(e -> getIfMatches(jar, e))
                        .filter(Objects::nonNull)
                        .findFirst().orElse(null))
                .filter(Objects::nonNull)
                .collect(toSet());
        if (entryJars.size() < entryPoints.size()) {
            if (entryJars.isEmpty()) {
                throw new JBuildException("Could not find any of the entry points in the input directory.", USER_INPUT);
            }
            var missingJars = entryPoints.stream()
                    .filter(Predicate.not(entryJars::contains))
                    .collect(toSet());
            throw new JBuildException("Could not find all entry points.\n" +
                    "Found jars: " + entryJars + "\n" +
                    "Missing jars: " + missingJars, USER_INPUT);
        }

        return jarSetPermutations.fromJars(jarFiles).thenComposeAsync((allJarSets) -> {
            var jarSets = eliminateJarSetsMissingEntrypoints(entryJars, allJarSets);

            if (jarSets.isEmpty()) {
                throw new JBuildException("Could not find any valid classpath permutation", ACTION_ERROR);
            }

            var checkResults = jarSets.stream().map(jarSet -> jarSet.toClassGraph().thenApplyAsync(classGraph -> {
                        // TODO re-implement
                        return ConsistencyCheckResult.success(jarSet.getJarFiles());
                    }).thenApplyAsync(results -> ClasspathCheckResult.of(results, jarSet)))
                    .collect(toList());

            return awaitValues(checkResults).thenApplyAsync(results -> {
                var bad = results.stream().map(e -> e.map(l -> null, r -> r))
                        .filter(Objects::nonNull)
                        .collect(toList());

                if (!bad.isEmpty()) {
                    throw new RuntimeException("ERRORS:" + bad, bad.get(0));
                }

                return results.stream().map(e -> e.map(l -> l, r -> null))
                        .filter(Objects::nonNull)
                        .collect(toList());
            });
        });
    }

    private List<JarSet> eliminateJarSetsMissingEntrypoints(Set<File> entryJars, List<JarSet> jarSets) {
        return jarSets.stream()
                .filter(jarSet -> {
                    var ok = jarSet.containsAll(entryJars);
                    if (log.isVerbose() && !ok) {
                        log.verbosePrintln("Eliminating classpath as it is missing at least one entry-point: " +
                                jarSet.toClasspath());
                    }
                    return ok;
                }).collect(toList());
    }

    private Void showClasspathCheckResults(Collection<ClasspathCheckResult> results) {
        var success = results.stream()
                .map(res -> res.successful ? res : null)
                .filter(Objects::nonNull)
                .findFirst();

        if (success.isPresent()) {
            showSuccessfulClasspath(success.get().jarSet);
            return null;
        }

        log.println("No classpath permutation could be found to satisfy all entrypoints, " +
                "try a different classpath or entrypoint!");

        showClasspathErrors(results);

        return null;
    }

    private void showClasspathErrors(Collection<ClasspathCheckResult> results) {
        for (var failureResult : results) {
            if (failureResult.aborted || failureResult.getErrors().isEmpty()) break;
            log.println(() -> LINE_END + "Attempted classpath: " + failureResult.jarSet.toClasspath());

            var errorCount = 0;
            var errorsGroupedByTarget = new LinkedHashMap<String, List<ClassPathInconsistency>>();
            for (var inconsistency : failureResult.getErrors().get()) {
                errorCount++;
                errorsGroupedByTarget.computeIfAbsent(inconsistency.to,
                        (k) -> new ArrayList<>()
                ).add(inconsistency);
            }

            log.println("✗ Found " + errorCount + " error" + (errorCount == 1 ? "" : "s") + ":");

            var reportable = log.isVerbose()
                    ? errorsGroupedByTarget
                    : CollectionUtils.take(errorsGroupedByTarget, 5);

            for (var error : reportable.entrySet()) {
                var target = error.getKey();
                log.println("  * " + target + " not found, but referenced from:");
                var problems = error.getValue().stream();
                var isHidingProblems = false;
                if (!log.isVerbose() && error.getValue().size() > 3) {
                    problems = problems.limit(3);
                    isHidingProblems = true;
                }
                problems.forEach(problem -> {
                    // TODO show all info possible
                    if (problem.referenceChain.isEmpty()) {
                        var from = problem.jarFrom == null ? "?" : problem.jarFrom;
                        log.println("    - " + from);
                    } else {
                        log.println("    - " + problem.referenceChain);
                    }
                });
                if (isHidingProblems) log.println("    ...");
            }
            if (reportable.size() < errorsGroupedByTarget.size() && log.isVerbose()) {
                log.println("  ... <enable verbose logging to see all " + errorCount + " errors>" + LINE_END);
            } else {
                log.println("");
            }
        }
        throw new JBuildException("No valid classpath could be found", ACTION_ERROR);
    }

    private void showSuccessfulClasspath(JarSet jarSet) {
        log.println("All entrypoint type dependencies are satisfied by the classpath below:" + LINE_END);
        log.println(jarSet.toClasspath());
    }

    private static File getIfMatches(File jar, File entryPoint) {
        var match = entryPoint.getName().contains(File.separator)
                ? jar.equals(entryPoint)
                : jar.getName().equals(entryPoint.getName());
        if (match) return jar;
        return null;
    }

    public static final class ClasspathCheckResult {

        private final NonEmptyCollection<ClassPathInconsistency> errors;
        public final boolean aborted;
        public final JarSet jarSet;
        public final boolean successful;

        public ClasspathCheckResult(JarSet jarSet,
                                    boolean aborted,
                                    NonEmptyCollection<ClassPathInconsistency> errors) {
            this.jarSet = jarSet;
            this.aborted = aborted;
            this.errors = errors;
            successful = !aborted && errors == null;
        }

        public static ClasspathCheckResult of(ConsistencyCheckResult result, JarSet jarSet) {
            var jars = result.isOk() ? jarSet.filterFiles(result.jars) : jarSet;
            var errors = result.isOk() ? null : result.inconsistencies;
            return new ClasspathCheckResult(jars, false, errors);
        }

        public Optional<NonEmptyCollection<ClassPathInconsistency>> getErrors() {
            return Optional.ofNullable(errors);
        }

        @Override
        public String toString() {
            return "ClasspathCheckResult{" +
                    "errors=" + errors +
                    ", aborted=" + aborted +
                    ", jarSet=" + jarSet +
                    ", successful=" + successful +
                    '}';
        }
    }

    private static final class ConsistencyCheckResult {

        public final NonEmptyCollection<ClassPathInconsistency> inconsistencies;
        public final Set<File> jars;

        ConsistencyCheckResult(NonEmptyCollection<ClassPathInconsistency> inconsistencies,
                               Set<File> jars) {
            this.inconsistencies = inconsistencies;
            this.jars = jars;
        }

        static ConsistencyCheckResult success(Set<File> jars) {
            assert jars != null;
            return new ConsistencyCheckResult(null, jars);
        }

        static ConsistencyCheckResult failure(NonEmptyCollection<ClassPathInconsistency> inconsistencies) {
            assert inconsistencies != null;
            return new ConsistencyCheckResult(inconsistencies, null);
        }

        boolean isOk() {
            return inconsistencies == null;
        }
    }

    public static final class ClassPathInconsistency {

        public final String referenceChain;
        // Java field/method/class?
        public final String to;
        public final File jarFrom;
        public final File jarTo;

        public ClassPathInconsistency(
                String referenceChain,
                String to,
                File jarFrom,
                File jarTo) {
            this.referenceChain = referenceChain;
            this.to = to;
            this.jarFrom = jarFrom;
            this.jarTo = jarTo;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (ClassPathInconsistency) obj;
            return Objects.equals(this.referenceChain, that.referenceChain) &&
                    Objects.equals(this.to, that.to) &&
                    Objects.equals(this.jarFrom, that.jarFrom) &&
                    Objects.equals(this.jarTo, that.jarTo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(referenceChain, to, jarFrom, jarTo);
        }

        @Override
        public String toString() {
            return "ClassPathInconsistency[" +
                    "referenceChain=" + referenceChain + ", " +
                    "to=" + to + ", " +
                    "jarFrom=" + jarFrom + ", " +
                    "jarTo=" + jarTo + ']';
        }

    }

}
