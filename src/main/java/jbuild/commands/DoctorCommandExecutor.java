package jbuild.commands;

import jbuild.errors.JBuildException;
import jbuild.java.ClassGraph;
import jbuild.java.Jar;
import jbuild.java.JarSet;
import jbuild.java.JarSetPermutations;
import jbuild.java.code.Code;
import jbuild.java.code.Definition;
import jbuild.log.JBuildLog;
import jbuild.util.Either;
import jbuild.util.JavaTypeUtils;
import jbuild.util.NonEmptyCollection;

import java.io.File;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.completedStage;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static jbuild.commands.InteractivityHelper.askYesOrNoQuestion;
import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;
import static jbuild.util.AsyncUtils.awaitSuccessValues;
import static jbuild.util.FileUtils.allFilesInDir;
import static jbuild.util.JavaTypeUtils.cleanArrayTypeName;
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
                                  boolean interactive,
                                  List<String> entryPoints,
                                  Set<Pattern> typeExclusions) {
        var results = findValidClasspaths(
                new File(inputDir),
                interactive,
                entryPoints.stream().map(File::new).collect(toList()),
                typeExclusions);
        return results.thenApply(this::showClasspathCheckResults);
    }

    public CompletionStage<List<Either<ClasspathCheckResult, Throwable>>> findValidClasspaths(
            File inputDir,
            boolean interactive,
            List<File> entryPoints,
            Set<Pattern> typeExclusions) {
        var jarFiles = allFilesInDir(inputDir, (dir, name) -> name.endsWith(".jar"));

        var entryJars = Stream.of(jarFiles)
                .map(jar -> entryPoints.stream()
                        .map(e -> getIfMatches(jar, e))
                        .filter(Objects::nonNull)
                        .findFirst().orElse(null))
                .filter(Objects::nonNull)
                .collect(toSet());
        if (entryJars.size() < entryPoints.size()) {
            throw new JBuildException("Could not find all entry points, found following jars: " + entryJars, USER_INPUT);
        }

        return jarSetPermutations.fromJars(jarFiles).thenComposeAsync((jarSets) -> {
            if (jarSets.isEmpty()) {
                throw new JBuildException("Could not find any valid classpath permutation", ACTION_ERROR);
            }

            // all jarSets contain the entry point jars, so we peek them from the first available one
            var entryPointJars = jarSets.iterator().next().getJars(entryJars);

            return computeEntryPointsTypeRequirements(entryPointJars, typeExclusions)
                    .thenApplyAsync(typeRequirements ->
                            findTypeCompleteClasspaths(interactive, jarSets, entryPointJars, typeRequirements))
                    .thenComposeAsync((goodJarSets) ->
                            findValidClasspaths(goodJarSets, entryJars, interactive, typeExclusions));
        });
    }

    private NonEmptyCollection<JarSet> findTypeCompleteClasspaths(boolean interactive,
                                                                  List<JarSet> jarSets,
                                                                  Set<Jar> entryPointJars,
                                                                  Set<String> typeRequirements) {
        var filteredJarSets = jarSets.stream()
                .map((jarSet) -> jarSet.filter(entryPointJars, typeRequirements)
                        .mapRight(errors -> new AbstractMap.SimpleEntry<>(jarSet, errors)))
                .collect(toList());

        var badResults = filteredJarSets.stream()
                .map(res -> res.map(ok -> null, err -> err))
                .filter(Objects::nonNull)
                .collect(toList());

        var goodResults = filteredJarSets.stream()
                .map(res -> res.map(ok -> ok, err -> null))
                .filter(Objects::nonNull)
                .collect(toList());

        if (log.isVerbose() && !goodResults.isEmpty()) {
            if (badResults.isEmpty()) {
                log.verbosePrintln("All " + goodResults.size() + " classpath(s) contain the types " +
                        "required by the entry-points");
            } else {
                log.verbosePrintln("Eliminated " + badResults.size() + " classpath(s) " +
                        "due to missing types required by the entry-points, " +
                        "but found " + goodResults.size() + " that can provide the required types");
            }
        }
        if (goodResults.isEmpty()) {
            if (interactive) {
                var answer = askYesOrNoQuestion("Failed to find any classpath that can provide " +
                        "all required types. Do you want to see the problems for each " +
                        "attempted classpath");
                if (answer) {
                    showErrors(badResults, true);
                }
            } else {
                showErrors(badResults, log.isVerbose());
            }
            throw new JBuildException("None of the classpaths could provide all types required by the " +
                    "entry-points. See log above for details.", ACTION_ERROR);
        }
        return NonEmptyCollection.of(goodResults);
    }

    private void showErrors(List<AbstractMap.SimpleEntry<JarSet, NonEmptyCollection<String>>> badResults,
                            boolean verbose) {
        var limit = verbose ? Integer.MAX_VALUE : 5;
        badResults.stream().limit(limit).forEach((badResult) -> {
            var errors = badResult.getValue().stream().collect(toSet());
            log.println(() -> "Found " + errors.size() + " errors in classpath:" + badResult.getKey().toClasspath());
            errors.stream().limit(limit).forEach(err -> log.println("  * " + err));
            if (errors.size() > limit) {
                log.println("  ...");
            }
        });
    }

    private CompletionStage<Set<String>> computeEntryPointsTypeRequirements(
            Set<Jar> jarSet,
            Set<Pattern> typeExclusions) {
        // ensure the entry points have been parsed so we can check if all their type requirements can be fulfilled
        var entryPoints = awaitSuccessValues(jarSet.stream()
                .map(Jar::parsed)
                .collect(toList()));

        return entryPoints.thenApplyAsync((jars) -> {
            var requiredTypes = new HashSet<String>(512);
            for (var entryPoint : jars) {
                entryPoint.collectTypesReferredToInto(requiredTypes);
            }
            return requiredTypes.stream()
                    .filter(not(JavaTypeUtils::mayBeJavaStdLibType))
                    .filter(type -> typeExclusions.stream().noneMatch(p -> p.matcher(type).matches()))
                    .collect(toSet());
        });
    }

    private CompletionStage<List<Either<ClasspathCheckResult, Throwable>>> findValidClasspaths(
            NonEmptyCollection<JarSet> jarSets,
            Set<File> entryJars,
            boolean interactive,
            Set<Pattern> typeExclusions) {
        var acceptableJarSets = jarSets.stream()
                .filter(jarset -> jarset.containsAll(entryJars))
                .collect(toList());

        if (acceptableJarSets.isEmpty()) {
            throw new JBuildException("Out of " + jarSets.stream().count() + " classpath permutations found, " +
                    "none includes all the entry points.", ACTION_ERROR);
        }

        if (acceptableJarSets.size() == 1) {
            log.println("Found a single classpath permutation, checking its consistency.");
        } else {
            log.println(() -> "Detected conflicts in classpath, resulting in " + acceptableJarSets.size() +
                    " possible classpath permutations. Trying to find a consistent permutation.");
        }

        var abort = new AtomicBoolean(false);
        var errorCount = new AtomicInteger(0);
        var badJarPairs = ConcurrentHashMap.<Map.Entry<File, File>>newKeySet(8);

        return new LimitedConcurrencyAsyncCompleter(interactive ? 1 : 2,
                acceptableJarSets.stream()
                        .map(c -> checkClasspath(c, entryJars, typeExclusions, abort, interactive, errorCount, badJarPairs))
                        .collect(toList())
        ).toList();
    }

    private Supplier<CompletionStage<ClasspathCheckResult>> checkClasspath(
            JarSet jarSet,
            Set<File> entryJars,
            Set<Pattern> typeExclusions,
            AtomicBoolean abort,
            boolean interactive,
            AtomicInteger errorCount,
            Set<Map.Entry<File, File>> badJarPairs) {
        return () -> {
            boolean skip = false, isInconsistent = false;
            if (abort.get()) {
                log.verbosePrintln(() -> "Aborting check of classpath: " + jarSet.toClasspath());
                skip = true;
            } else {
                isInconsistent = jarSet.containsAny(badJarPairs);
                if (isInconsistent) skip = true;
            }

            if (interactive && !skip) {
                log.println("Next classpath:" + LINE_END + jarSet.toClasspath());
                var keepGoing = askYesOrNoQuestion("Do you want to check this classpath");
                if (!keepGoing) {
                    abort.set(true);
                    skip = true;
                }
            }

            if (skip) {
                if (isInconsistent) {
                    var badClasspaths = errorCount.incrementAndGet();
                    log.println(() -> badClasspaths + " inconsistent classpath" +
                            (badClasspaths == 1 ? "" : "s") + " checked so far... " +
                            "latest classpath contained known incompatible jars: " + badJarPairs.stream()
                            .map(pair -> pair.getKey().getName() + " ✗ " + pair.getValue().getName())
                            .collect(joining(" | ")));
                }
                return completedStage(new ClasspathCheckResult(jarSet, true, List.of()));
            }

            log.verbosePrintln(() -> "Checking classpath: " + jarSet.toClasspath());

            return jarSet.toClassGraph().thenApply(graph -> {
                var result = checkClasspathConsistency(graph, entryJars, typeExclusions);
                if (result.isEmpty()) {
                    abort.set(true); // success found!
                } else {
                    for (var classPathInconsistency : result) {
                        if (classPathInconsistency.jarFrom != null && classPathInconsistency.jarTo != null) {
                            badJarPairs.add(new AbstractMap.SimpleEntry<>(
                                    classPathInconsistency.jarFrom, classPathInconsistency.jarTo));
                        }
                    }
                    if (interactive) {
                        log.println("Classpath is inconsistent!");
                    }
                    var badClasspaths = errorCount.incrementAndGet();
                    log.println(() -> badClasspaths + " inconsistent classpath" +
                            (badClasspaths == 1 ? "" : "s") + " checked so far.");
                }
                return new ClasspathCheckResult(jarSet, false, result);
            });
        };
    }

    private List<ClassPathInconsistency> checkClasspathConsistency(
            ClassGraph classGraph,
            Set<File> entryJars,
            Set<Pattern> typeExclusions) {
        var allErrors = new ArrayList<ClassPathInconsistency>();

        for (var jar : entryJars) {
            var startTime = System.currentTimeMillis();
            var initialErrorCount = allErrors.size();
            for (var entry : classGraph.getTypesByJar().get(jar).entrySet()) {
                var type = entry.getKey();
                if (isIgnored(type, typeExclusions)) continue;
                var typeDef = entry.getValue();
                typeDef.type.typesReferredTo().forEach(ref -> {
                    if (!isIgnored(ref, typeExclusions) && !classGraph.exists(ref)) {
                        allErrors.add(new ClassPathInconsistency(
                                "Type " + ref + ", used in type signature of " +
                                        jar.getName() + "!" + type + " cannot be found",
                                jar, classGraph.getJarByType().get(ref)));
                    }
                });
                for (var methodHandle : typeDef.usedMethodHandles) {
                    if (isIgnored(methodHandle.typeName, typeExclusions)) continue;
                    var error = errorIfMethodDoesNotExist(type, jar, null, classGraph, methodHandle, "MethodHandle");
                    if (error != null) allErrors.add(error);
                }
                typeDef.methods.forEach((method, codes) -> {
                    for (var code : codes) {
                        var error = errorIfCodeRefDoesNotExist(classGraph, type, jar, method, code, typeExclusions);
                        if (error != null) allErrors.add(error);
                    }
                });
            }

            log.verbosePrintln(() -> "Checked " + jar.getName() + " classpath requirements in " +
                    (System.currentTimeMillis() - startTime) + " ms, ok? " + (allErrors.size() == initialErrorCount));
        }

        return allErrors;
    }

    private ClassPathInconsistency errorIfCodeRefDoesNotExist(ClassGraph classGraph,
                                                              String typeFrom,
                                                              File jarFrom,
                                                              Definition.MethodDefinition methodFrom,
                                                              Code target,
                                                              Set<Pattern> typeExclusions) {
        var typeName = cleanArrayTypeName(target.typeName);
        if (isIgnored(typeName, typeExclusions)) return null;
        var error = errorIfTypeDoesNotExist(typeFrom, jarFrom, methodFrom, classGraph, typeName);
        if (error != null) return error;
        return target.match(
                t -> null,
                f -> errorIfFieldDoesNotExist(typeFrom, jarFrom, methodFrom, classGraph, f),
                m -> errorIfMethodDoesNotExist(typeFrom, jarFrom, methodFrom, classGraph, m, "Method"));
    }

    private static ClassPathInconsistency errorIfTypeDoesNotExist(String typeFrom,
                                                                  File jarFrom,
                                                                  Definition.MethodDefinition methodFrom,
                                                                  ClassGraph classGraph,
                                                                  String targetType) {

        if (!classGraph.getJarByType().containsKey(targetType) &&
                !classGraph.existsJava(targetType)) {
            return new ClassPathInconsistency("Type " + targetType +
                    ", used in method " + methodFrom.descriptor() + " of " +
                    jarFrom.getName() + "!" + typeFrom + " cannot be found in the classpath",
                    jarFrom, null);
        }
        return null;
    }

    private ClassPathInconsistency errorIfFieldDoesNotExist(String type,
                                                            File jar,
                                                            Definition.MethodDefinition methodDef,
                                                            ClassGraph classGraph,
                                                            Code.Field field) {
        var fieldOwner = field.typeName;
        var targetField = new Definition.FieldDefinition(field.name, field.type);
        var targetJar = classGraph.getJarByType().get(fieldOwner);
        if (targetJar == null) {
            return classGraph.existsJava(fieldOwner, targetField) ? null :
                    new ClassPathInconsistency("Field " + targetField.descriptor() +
                            ", used in method " + methodDef.descriptor() + " of " +
                            jar.getName() + "!" + type + " cannot be found as there is no such field in " + fieldOwner,
                            jar, null);
        }
        if (jar.equals(targetJar)) return null; // do not check same-jar relations
        if (classGraph.exists(fieldOwner, targetField)) {
            return null;
        }
        var typeDef = classGraph.getTypeDefinition(fieldOwner);
        assert typeDef != null;
        log.verbosePrintln(() -> {
            var fields = typeDef.fields.stream()
                    .map(Definition.FieldDefinition::descriptor)
                    .collect(joining(", ", "[", "]"));
            return "Could not find " + targetField.descriptor() + " in " + typeDef.typeName +
                    ", available fields are " + fields;
        });
        return new ClassPathInconsistency("Field " + targetField.descriptor() +
                ", used in method " + methodDef.descriptor() + " of " +
                jar.getName() + "!" + type + " cannot be found as there is no such field in " +
                targetJar + "!" + typeDef.typeName,
                jar, targetJar);
    }

    private ClassPathInconsistency errorIfMethodDoesNotExist(String type,
                                                             File jar,
                                                             Definition.MethodDefinition methodDef,
                                                             ClassGraph classGraph,
                                                             Code.Method method,
                                                             String methodKind) {
        var methodOwner = method.typeName;
        var targetMethod = new Definition.MethodDefinition(method.name, method.type);
        var targetJar = classGraph.getJarByType().get(methodOwner);
        if (targetJar == null) {
            return classGraph.existsJava(methodOwner, targetMethod) ? null :
                    new ClassPathInconsistency(methodKind + " " + targetMethod.descriptor() + ", used in " +
                            (methodDef == null ? "" : "method " + methodDef.descriptor() + " of ") +
                            jar.getName() + "!" + type + " cannot be found as there is no such method in " + methodOwner,
                            jar, null);
        }
        if (jar.equals(targetJar)) return null; // do not check same-jar relations
        if (classGraph.exists(methodOwner, targetMethod)) {
            return null;
        }
        var typeDef = classGraph.getTypeDefinition(methodOwner);
        assert typeDef != null;
        log.verbosePrintln(() -> {
            var methods = typeDef.methods.keySet().stream()
                    .map(Definition.MethodDefinition::descriptor)
                    .collect(joining(", ", "[", "]"));
            return "Could not find " + targetMethod.descriptor() + " in " + typeDef.typeName +
                    ", available methods are " + methods;
        });
        return new ClassPathInconsistency(methodKind + " " + targetMethod.descriptor() + ", used in " +
                (methodDef == null ? "" : "method " + methodDef.descriptor() + " of ") +
                jar.getName() + "!" + type + " cannot be found as there is no such method in " +
                targetJar + "!" + typeDef.typeName,
                jar, targetJar);
    }

    private Void showClasspathCheckResults(Collection<Either<ClasspathCheckResult, Throwable>> results) {
        var success = results.stream()
                .map(e -> e.map(ok -> ok.successful ? ok : null, err -> null))
                .filter(Objects::nonNull)
                .findFirst();

        if (success.isPresent()) {
            showSuccessfulClasspath(success.get().jarSet);
        } else {
            log.println("No classpath permutation could be found to satisfy all entrypoints, " +
                    "try a different classpath or entrypoint!");

            var failures = results.stream()
                    .map(e -> e.map(
                            Either::<ClasspathCheckResult, Throwable>left,
                            Either::<ClasspathCheckResult, Throwable>right))
                    .collect(toList());

            for (var result : failures) {
                result.use(failureResult -> {
                    if (failureResult.aborted) return;
                    log.println(() -> LINE_END + "Attempted classpath: " + failureResult.jarSet.toClasspath());
                    var errorCount = failureResult.errors.size();
                    log.println("✗ Found " + errorCount + " error" + (errorCount == 1 ? "" : "s") + ":");
                    var reportable = errorCount > 5 && !log.isVerbose()
                            ? failureResult.errors.subList(0, 5)
                            : failureResult.errors;
                    for (var error : reportable) {
                        log.println("  * " + error.message);
                    }
                    if (reportable.size() < failureResult.errors.size()) {
                        log.println(() -> "  ... <enable verbose logging to see all " + errorCount + " errors>" + LINE_END);
                    } else {
                        log.println("");
                    }
                }, throwable -> {
                    log.println(LINE_END + "Error trying to verify classpath consistency:");
                    log.print(throwable);
                });

            }
        }
        return null;
    }

    private void showSuccessfulClasspath(JarSet jarSet) {
        log.println("All entrypoint type dependencies are satisfied by the classpath below:" + LINE_END);
        log.println(jarSet.toClasspath());
    }

    private static boolean isIgnored(String typeName, Set<Pattern> typeExclusions) {
        if (typeExclusions.isEmpty()) return false;
        for (var exclusion : typeExclusions) {
            if (exclusion.matcher(typeName).matches()) {
                return true;
            }
        }
        return false;
    }

    private static File getIfMatches(File jar, File entryPoint) {
        var match = entryPoint.getName().contains(File.separator)
                ? jar.equals(entryPoint)
                : jar.getName().equals(entryPoint.getName());
        if (match) return jar;
        return null;
    }

    public static final class ClasspathCheckResult {

        public final List<ClassPathInconsistency> errors;
        public final boolean aborted;
        public final JarSet jarSet;
        public final boolean successful;

        public ClasspathCheckResult(JarSet jarSet,
                                    boolean aborted,
                                    List<ClassPathInconsistency> errors) {
            this.jarSet = jarSet;
            this.aborted = aborted;
            this.errors = errors;
            successful = !aborted && errors.isEmpty();
        }
    }

    public static final class ClassPathInconsistency {
        public final String message;
        public final File jarFrom;
        public final File jarTo;

        public ClassPathInconsistency(String message, File jarFrom, File jarTo) {
            this.message = message;
            this.jarFrom = jarFrom;
            this.jarTo = jarTo;
        }
    }

    private static final class LimitedConcurrencyAsyncCompleter {

        private final int maxConcurrentCompletions;
        private final int totalCompletions;
        private final Deque<Supplier<CompletionStage<ClasspathCheckResult>>> completions;
        private final List<Either<ClasspathCheckResult, Throwable>> results;
        private final CompletableFuture<List<Either<ClasspathCheckResult, Throwable>>> futureResult;


        public LimitedConcurrencyAsyncCompleter(int maxConcurrentCompletions,
                                                List<Supplier<CompletionStage<ClasspathCheckResult>>> completions) {
            this.maxConcurrentCompletions = maxConcurrentCompletions;
            this.totalCompletions = completions.size();
            this.completions = new ConcurrentLinkedDeque<>(completions);
            this.results = new ArrayList<>(completions.size());
            this.futureResult = new CompletableFuture<>();
        }

        public CompletionStage<List<Either<ClasspathCheckResult, Throwable>>> toList() {
            for (var i = 0; i < maxConcurrentCompletions; i++) {
                var nextStage = completions.poll();
                if (nextStage == null) break;
                runAsync(() -> next(nextStage));
            }
            return futureResult;
        }

        private void next(Supplier<CompletionStage<ClasspathCheckResult>> stageSupplier) {
            BiConsumer<ClasspathCheckResult, Throwable> onDone = (ok, err) -> {
                synchronized (results) {
                    results.add(err == null ? Either.left(ok) : Either.right(err));
                }
                if (results.size() == totalCompletions) {
                    futureResult.complete(results);
                } else {
                    var nextStage = completions.poll();
                    if (nextStage != null) next(nextStage);
                }
            };

            try {
                stageSupplier.get().whenComplete(onDone);
            } catch (Throwable e) {
                onDone.accept(null, e);
            }
        }

    }
}
