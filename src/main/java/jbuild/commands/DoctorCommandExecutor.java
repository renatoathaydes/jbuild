package jbuild.commands;

import jbuild.errors.JBuildException;
import jbuild.java.ClassGraph;
import jbuild.java.ClassGraphLoader;
import jbuild.java.JarSet;
import jbuild.java.code.Code;
import jbuild.java.code.Definition;
import jbuild.log.JBuildLog;
import jbuild.util.Either;

import java.io.File;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
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
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.completedStage;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static jbuild.commands.InteractivityHelper.askYesOrNoQuestion;
import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;
import static jbuild.util.FileUtils.allFilesInDir;
import static jbuild.util.JavaTypeUtils.cleanArrayTypeName;
import static jbuild.util.TextUtils.LINE_END;

public final class DoctorCommandExecutor {

    private final JBuildLog log;
    private final ClassGraphLoader classGraphLoader;

    public DoctorCommandExecutor(JBuildLog log) {
        this(log, ClassGraphLoader.create(log));
    }

    public DoctorCommandExecutor(JBuildLog log,
                                 ClassGraphLoader classGraphLoader) {
        this.log = log;
        this.classGraphLoader = classGraphLoader;
    }

    public CompletionStage<?> run(String inputDir, boolean interactive, List<String> entryPoints) {
        var results = findClasspathPermutations(
                new File(inputDir),
                interactive,
                entryPoints.stream().map(File::new).collect(toList()));
        return results.thenApply(this::showClasspathCheckResults);
    }

    public CompletionStage<List<Either<ClasspathCheckResult, Throwable>>> findClasspathPermutations(
            File inputDir,
            boolean interactive,
            List<File> entryPoints) {
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
        var classGraphCompletions = classGraphLoader.fromJars(jarFiles);

        if (classGraphCompletions.isEmpty()) {
            throw new JBuildException("Could not find any valid classpath permutation", ACTION_ERROR);
        }

        return findClasspathPermutations(classGraphCompletions, entryJars, interactive);
    }

    private CompletionStage<List<Either<ClasspathCheckResult, Throwable>>> findClasspathPermutations(
            List<ClassGraphLoader.ClassGraphCompletion> classGraphCompletions,
            Set<File> entryJars,
            boolean interactive) {
        var acceptableCompletions = classGraphCompletions.stream()
                .filter(c -> c.jarset.containsAll(entryJars))
                .collect(toList());

        if (acceptableCompletions.isEmpty()) {
            throw new JBuildException("Out of " + classGraphCompletions.size() + " classpath permutations found, " +
                    "none includes all the entry points.", ACTION_ERROR);
        }

        if (acceptableCompletions.size() == 1) {
            log.println("Found a single classpath permutation, checking its consistency.");
        } else {
            log.println(() -> "Detected conflicts in classpath, resulting in " + acceptableCompletions.size() +
                    " possible classpath permutations. Trying to find a consistent permutation.");
        }

        var abort = new AtomicBoolean(false);
        var errorCount = new AtomicInteger(0);
        var badJarPairs = ConcurrentHashMap.<Map.Entry<File, File>>newKeySet(8);

        return new LimitedConcurrencyAsyncCompleter(interactive ? 1 : 2,
                acceptableCompletions.stream()
                        .map(c -> checkClasspath(c, entryJars, abort, interactive, errorCount, badJarPairs))
                        .collect(toList())
        ).toList();
    }

    private Supplier<CompletionStage<ClasspathCheckResult>> checkClasspath(
            ClassGraphLoader.ClassGraphCompletion completion,
            Set<File> entryJars,
            AtomicBoolean abort,
            boolean interactive,
            AtomicInteger errorCount,
            Set<Map.Entry<File, File>> badJarPairs) {
        return () -> {
            var skip = false;
            if (abort.get()) {
                log.verbosePrintln(() -> "Aborting check of classpath: " + completion.jarset.toClasspath());
            } else {
                skip = completion.jarset.containsAny(badJarPairs);
                if (!skip) {
                    if (interactive) {
                        log.println("Next classpath:" + LINE_END + completion.jarset.toClasspath());
                        var keepGoing = askYesOrNoQuestion("Do you want to continue");
                        if (!keepGoing) abort.set(true);
                    } else {
                        log.verbosePrintln(() -> "Trying classpath: " + completion.jarset.toClasspath());
                    }
                }
            }

            if (skip || abort.get()) {
                if (skip) {
                    var badClasspaths = errorCount.incrementAndGet();
                    log.println(() -> badClasspaths + " inconsistent classpath" +
                            (badClasspaths == 1 ? "" : "s") + " checked so far... " +
                            "latest classpath contained known incompatible jars: " + badJarPairs.stream()
                            .map(pair -> pair.getKey().getName() + " ✗ " + pair.getValue().getName())
                            .collect(joining(" | ")));
                }
                return completedStage(new ClasspathCheckResult(completion.jarset, true, List.of()));
            }

            return completion.getCompletion().thenApply(ok -> {
                var result = checkClasspathConsistency(ok, entryJars);
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
                return new ClasspathCheckResult(completion.jarset, false, result);
            });
        };
    }

    private List<ClassPathInconsistency> checkClasspathConsistency(
            ClassGraph classGraph,
            Set<File> entryJars) {
        var allErrors = new ArrayList<ClassPathInconsistency>();

        for (var jar : entryJars) {
            var startTime = System.currentTimeMillis();
            var initialErrorCount = allErrors.size();
            for (var entry : classGraph.getTypesByJar().get(jar).entrySet()) {
                var type = entry.getKey();
                var typeDef = entry.getValue();
                typeDef.type.typesReferredTo().forEach(ref -> {
                    if (!classGraph.exists(ref)) {
                        allErrors.add(new ClassPathInconsistency(
                                "Type " + ref + ", used in type signature of " +
                                        jar.getName() + "!" + type + " cannot be found",
                                jar, classGraph.getJarByType().get(ref)));
                    }
                });
                for (var methodHandle : typeDef.methodHandles) {
                    var error = errorIfMethodDoesNotExist(type, jar, null, classGraph, methodHandle, "MethodHandle");
                    if (error != null) allErrors.add(error);
                }
                typeDef.methods.forEach((method, codes) -> {
                    for (var code : codes) {
                        var error = errorIfCodeRefDoesNotExist(classGraph, type, jar, method, code);
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
                                                              String type,
                                                              File jar,
                                                              Definition.MethodDefinition method,
                                                              Code code) {
        return code.match(
                t -> errorIfTypeDoesNotExist(type, jar, method, classGraph, t.typeName),
                f -> firstNonNull(
                        errorIfTypeDoesNotExist(type, jar, method, classGraph, f.typeName),
                        () -> errorIfFieldDoesNotExist(type, jar, method, classGraph, f)),
                m -> firstNonNull(
                        errorIfTypeDoesNotExist(type, jar, method, classGraph, m.typeName),
                        () -> errorIfMethodDoesNotExist(type, jar, method, classGraph, m, "Method")));
    }

    private static ClassPathInconsistency errorIfTypeDoesNotExist(String type,
                                                                  File jar,
                                                                  Definition.MethodDefinition methodDef,
                                                                  ClassGraph classGraph,
                                                                  String targetType) {
        var typeName = cleanArrayTypeName(targetType);
        if (!classGraph.getJarByType().containsKey(typeName) && !classGraph.existsJava(typeName)) {
            return new ClassPathInconsistency("Type " + typeName + ", used in method " + methodDef.descriptor() + " of " +
                    jar.getName() + "!" + type + " cannot be found in the classpath",
                    jar, null);
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
            log.verbosePrintln(() -> "Field type owner " + fieldOwner +
                    " not found in any jar, checking if it is a Java API");
            return classGraph.existsJava(fieldOwner, targetField) ? null :
                    new ClassPathInconsistency("Field " + targetField.descriptor() + ", used in method " + methodDef.descriptor() + " of " +
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
        return new ClassPathInconsistency("Field " + targetField.descriptor() + ", used in method " + methodDef.descriptor() + " of " +
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
            log.verbosePrintln(() -> "Method type owner " + methodOwner +
                    " not found in any jar, checking if it is a Java API");
            return classGraph.existsJava(methodOwner, targetMethod) ? null :
                    new ClassPathInconsistency(methodKind + " " + targetMethod.descriptor() + ", used in " +
                            (methodDef == null ? "" : " method " + methodDef.descriptor() + " of ") +
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
                (methodDef == null ? "" : " method " + methodDef.descriptor() + " of ") +
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

    private static File getIfMatches(File jar, File entryPoint) {
        var match = entryPoint.getName().contains(File.separator)
                ? jar.equals(entryPoint)
                : jar.getName().equals(entryPoint.getName());
        if (match) return jar;
        return null;
    }

    private static <T> T firstNonNull(T value, Supplier<T> other) {
        return value == null ? other.get() : value;
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
