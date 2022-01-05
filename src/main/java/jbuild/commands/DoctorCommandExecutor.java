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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.completedStage;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static jbuild.commands.InteractivityHelper.askYesOrNoQuestion;
import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;
import static jbuild.util.AsyncUtils.awaitValues;
import static jbuild.util.FileUtils.allFilesInDir;
import static jbuild.util.JavaTypeUtils.cleanArrayTypeName;
import static jbuild.util.TextUtils.firstNonBlank;

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
        var results = findClasspathPermutations(inputDir, interactive, entryPoints);
        return awaitValues(results).thenApply(this::showClasspathCheckResults);
    }

    public List<CompletionStage<ClasspathCheckResult>> findClasspathPermutations(String inputDir,
                                                                                 boolean interactive,
                                                                                 List<String> entryPoints) {
        var jarFiles = allFilesInDir(inputDir, (dir, fname) -> fname.endsWith(".jar"));
        var entryJars = Stream.of(jarFiles)
                .filter(jar -> entryPoints.stream().anyMatch(e -> includes(jar, e)))
                .map(File::getPath)
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

    private List<CompletionStage<ClasspathCheckResult>> findClasspathPermutations(
            List<ClassGraphLoader.ClassGraphCompletion> classGraphCompletions,
            Set<String> entryJars,
            boolean interactive) {
        var acceptableCompletions = classGraphCompletions.stream()
                .filter(c -> c.jarset.containsAll(entryJars))
                .collect(toList());

        if (acceptableCompletions.isEmpty()) {
            throw new JBuildException("Out of " + classGraphCompletions.size() + " classpath permutations found, " +
                    "none includes all the entry points.", ACTION_ERROR);
        }

        if (acceptableCompletions.size() == 1) {
            log.println("Found a single valid classpath, checking its consistency.");
        } else {
            log.println(() -> "Detected conflicts in classpath, resulting in " + acceptableCompletions.size() +
                    " possible classpath permutations. Trying to find a consistent permutation.");
        }

        var abort = new AtomicBoolean(false);
        var errorCount = new AtomicInteger(0);
        var interactionBlocker = new LinkedBlockingDeque<Boolean>(1);
        interactionBlocker.offer(Boolean.TRUE);

        var completionStream = interactive
                ? acceptableCompletions.stream()
                : acceptableCompletions.stream().parallel();

        return completionStream
                .map(c -> checkClasspath(c, entryJars, abort, interactive, interactionBlocker, errorCount))
                .collect(toList());
    }

    private CompletionStage<ClasspathCheckResult> checkClasspath(
            ClassGraphLoader.ClassGraphCompletion completion,
            Set<String> entryJars,
            AtomicBoolean abort,
            boolean interactive,
            LinkedBlockingDeque<Boolean> interactionBlocker,
            AtomicInteger errorCount) {
        if (abort.get()) {
            log.verbosePrintln(() -> "Aborting check of classpath: " + completion.jarset.toClasspath());
        } else {
            if (interactive) {
                try {
                    interactionBlocker.poll(10, TimeUnit.DAYS);
                } catch (InterruptedException e) {
                    log.print(e);
                }
                log.println("Next classpath:\n" + completion.jarset.toClasspath());
                var keepGoing = askYesOrNoQuestion("Do you want to continue");
                if (!keepGoing) abort.set(true);
            } else {
                log.verbosePrintln(() -> "Trying classpath: " + completion.jarset.toClasspath());
            }
        }

        if (abort.get()) {
            return completedStage(new ClasspathCheckResult(completion.jarset,
                    List.of("Classpath check was aborted")));
        }

        return completion.getCompletion().thenApply(ok -> {
            var result = checkClasspathConsistency(ok, entryJars);
            if (result.isEmpty()) {
                abort.set(true);
            } else {
                if (interactive) {
                    log.println("Classpath is inconsistent!");
                }
                var badClasspaths = errorCount.incrementAndGet();
                log.println(() -> badClasspaths + " inconsistent classpath" +
                        (badClasspaths == 1 ? "" : "s") + " checked so far.");
            }
            interactionBlocker.offer(Boolean.TRUE);
            return new ClasspathCheckResult(completion.jarset, result);
        });
    }

    private List<String> checkClasspathConsistency(ClassGraph classGraph,
                                                   Set<String> entryJars) {
        var allErrors = new ArrayList<String>();

        for (var jar : entryJars) {
            var startTime = System.currentTimeMillis();
            var initialErrorCount = allErrors.size();
            for (var entry : classGraph.getTypesByJar().get(jar).entrySet()) {
                var type = entry.getKey();
                var typeDef = entry.getValue();
                typeDef.type.typesReferredTo().forEach(ref -> {
                    if (!classGraph.exists(ref)) {
                        allErrors.add("Type " + ref + ", used in type signature of " +
                                jar + "!" + type + " cannot be found");
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

            log.verbosePrintln(() -> "Checked " + jar + " classpath requirements in " +
                    (System.currentTimeMillis() - startTime) + " ms, ok? " + (allErrors.size() == initialErrorCount));
        }

        return allErrors;
    }

    private String errorIfCodeRefDoesNotExist(ClassGraph classGraph,
                                              String type,
                                              String jar,
                                              Definition.MethodDefinition method,
                                              Code code) {
        return code.match(
                t -> errorIfTypeDoesNotExist(type, jar, method, classGraph, t.typeName),
                f -> firstNonBlank(
                        errorIfTypeDoesNotExist(type, jar, method, classGraph, f.typeName),
                        () -> errorIfFieldDoesNotExist(type, jar, method, classGraph, f)),
                m -> firstNonBlank(
                        errorIfTypeDoesNotExist(type, jar, method, classGraph, m.typeName),
                        () -> errorIfMethodDoesNotExist(type, jar, method, classGraph, m, "Method")));
    }

    private static String errorIfTypeDoesNotExist(String type,
                                                  String jar,
                                                  Definition.MethodDefinition methodDef,
                                                  ClassGraph classGraph,
                                                  String targetType) {
        var typeName = cleanArrayTypeName(targetType);
        if (!classGraph.getJarByType().containsKey(typeName) && !classGraph.existsJava(typeName)) {
            return "Type " + typeName + ", used in method " + methodDef.descriptor() + " of " +
                    jar + "!" + type + " cannot be found in the classpath";
        }
        return null;
    }

    private String errorIfFieldDoesNotExist(String type,
                                            String jar,
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
                    "Field " + targetField.descriptor() + ", used in method " + methodDef.descriptor() + " of " +
                            jar + "!" + type + " cannot be found as there is no such field in " + fieldOwner;
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
        return "Field " + targetField.descriptor() + ", used in method " + methodDef.descriptor() + " of " +
                jar + "!" + type + " cannot be found as there is no such field in " +
                targetJar + "!" + typeDef.typeName;
    }

    private String errorIfMethodDoesNotExist(String type,
                                             String jar,
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
                    methodKind + " " + targetMethod.descriptor() + ", used in " +
                            (methodDef == null ? "" : " method " + methodDef.descriptor() + " of ") +
                            jar + "!" + type + " cannot be found as there is no such method in " + methodOwner;
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
        return methodKind + " " + targetMethod.descriptor() + ", used in " +
                (methodDef == null ? "" : " method " + methodDef.descriptor() + " of ") +
                jar + "!" + type + " cannot be found as there is no such method in " +
                targetJar + "!" + typeDef.typeName;
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
                    log.println(() -> "\nAttempted classpath: " + failureResult.jarSet.toClasspath());
                    log.println("Errors:");
                    var errorCount = failureResult.errors.size();
                    var reportable = errorCount > 5 && !log.isVerbose()
                            ? failureResult.errors.subList(0, 5)
                            : failureResult.errors;
                    for (String error : reportable) {
                        log.println("  * " + error);
                    }
                    if (reportable.size() < failureResult.errors.size()) {
                        log.println("  ... <enable verbose logging to see all errors>\n");
                    } else {
                        log.println("");
                    }
                }, throwable -> {
                    log.println("\nError trying to verify classpath consistency:");
                    log.print(throwable);
                });

            }
        }
        return null;
    }

    private void showSuccessfulClasspath(JarSet jarSet) {
        log.println("All entrypoint type dependencies are satisfied by the classpath below:\n");
        log.println(jarSet.toClasspath());
    }

    private static boolean includes(File jar, String entryPoint) {
        return jar.getName().equals(entryPoint) || jar.getPath().equals(entryPoint);
    }

    public static final class ClasspathCheckResult {

        public final List<String> errors;
        public final JarSet jarSet;
        public final boolean successful;

        public ClasspathCheckResult(JarSet jarSet, List<String> errors) {
            this.jarSet = jarSet;
            this.errors = errors;
            successful = errors.isEmpty();
        }
    }
}
