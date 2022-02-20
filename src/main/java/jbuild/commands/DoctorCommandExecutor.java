package jbuild.commands;

import jbuild.errors.JBuildException;
import jbuild.java.ClassGraph;
import jbuild.java.Jar;
import jbuild.java.JarSet;
import jbuild.java.JarSetPermutations;
import jbuild.java.TypeReference;
import jbuild.java.code.Code;
import jbuild.java.code.Definition;
import jbuild.log.JBuildLog;
import jbuild.util.JarFileFilter;
import jbuild.util.NoOp;
import jbuild.util.NonEmptyCollection;

import java.io.File;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.completedStage;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static jbuild.commands.InteractivityHelper.askYesOrNoQuestion;
import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;
import static jbuild.util.AsyncUtils.awaitSuccessValues;
import static jbuild.util.FileUtils.allFilesInDir;
import static jbuild.util.JavaTypeUtils.cleanArrayTypeName;
import static jbuild.util.JavaTypeUtils.typeNameToClassName;
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

    public CompletionStage<List<ClasspathCheckResult>> findValidClasspaths(
            File inputDir,
            boolean interactive,
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

            // all jarSets contain the entry point jars, so we peek them from the first available one
            var entryPointJars = jarSets.iterator().next().getJars(entryJars);

            return computeEntryPointsTypeRequirements(entryPointJars, typeExclusions)
                    .thenApplyAsync(typeRequirements ->
                            findTypeCompleteClasspaths(interactive, jarSets, typeRequirements))
                    .thenComposeAsync((goodJarSets) ->
                            findValidClasspaths(goodJarSets, entryJars, interactive, typeExclusions));
        });
    }

    private NonEmptyCollection<JarSet> findTypeCompleteClasspaths(boolean interactive,
                                                                  List<JarSet> jarSets,
                                                                  List<TypeReference> typeRequirements) {
        log.verbosePrintln(() -> "Entry-points required types: " + String.join(", ",
                typeRequirements.stream().flatMap(ref -> ref.typesTo.stream()).collect(toSet())));

        var filteredJarSets = jarSets.stream()
                .map((jarSet) -> jarSet.checkReferencesExist(typeRequirements)
                        .mapRight(errors -> new AbstractMap.SimpleEntry<>(jarSet, errors)))
                .collect(toList());

        var badResults = filteredJarSets.stream()
                .map(res -> res.map(NoOp.fun(), Function.identity()))
                .filter(Objects::nonNull)
                .collect(toList());

        var goodResults = filteredJarSets.stream()
                .map(res -> res.map(Function.identity(), NoOp.fun()))
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

    private void showErrors(List<AbstractMap.SimpleEntry<JarSet, NonEmptyCollection<String>>> badResults,
                            boolean verbose) {
        var limit = verbose ? Integer.MAX_VALUE : 5;
        badResults.stream().limit(limit).forEach((badResult) -> {
            var errors = badResult.getValue().stream()
                    .collect(toCollection(TreeSet::new));
            log.println(() -> "Found " + errors.size() + " error" +
                    (errors.size() == 1 ? "" : "s") +
                    " in classpath: " + badResult.getKey().toClasspath());
            errors.stream().limit(limit).forEach(err -> log.println("  * " + err));
            if (errors.size() > limit) {
                log.println("  ...");
            }
        });
    }

    private CompletionStage<List<TypeReference>> computeEntryPointsTypeRequirements(
            Set<Jar> jarSet,
            Set<Pattern> typeExclusions) {
        // ensure the entry points have been parsed so we can check if all their type requirements can be fulfilled
        var entryPoints = awaitSuccessValues(jarSet.stream()
                .map(Jar::parsed)
                .collect(toList()));

        return entryPoints.thenApplyAsync((jars) -> {
            Stream<TypeReference> requirements = Stream.of();
            for (var entryPoint : jars) {
                var requiredTypes = new ArrayList<TypeReference>(512);
                entryPoint.collectTypesReferredToInto(requiredTypes, typeExclusions);
                requirements = Stream.concat(requirements, requiredTypes.stream());
            }
            return requirements.collect(toList());
        });
    }

    private CompletionStage<List<ClasspathCheckResult>> findValidClasspaths(
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

        return new LimitedConcurrencyAsyncCompleter(interactive ? 1 : Runtime.getRuntime().availableProcessors(),
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
                return completedStage(new ClasspathCheckResult(jarSet, true, null));
            }

            log.verbosePrintln(() -> "Checking classpath: " + jarSet.toClasspath());

            return jarSet.toClassGraph().thenApply(graph -> {
                var result = checkClasspathConsistency(graph, entryJars, typeExclusions);
                if (result.isOk()) {
                    abort.set(true); // success found!
                    return new ClasspathCheckResult(jarSet.filter(result.jars), false, null);
                }
                for (var classPathInconsistency : result.inconsistencies) {
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
                return new ClasspathCheckResult(jarSet, false, result.inconsistencies);
            });
        };
    }

    private ConsistencyCheckResult checkClasspathConsistency(
            ClassGraph classGraph,
            Set<File> entryJars,
            Set<Pattern> typeExclusions) {
        var allErrors = new ArrayList<ClassPathInconsistency>();
        var usedJars = new HashSet<File>(classGraph.getJarByType().size());
        usedJars.addAll(entryJars);

        // TODO to be replaced with CallHierarchyVisitor
        for (var jar : entryJars) {
            var startTime = System.currentTimeMillis();
            var initialErrorCount = allErrors.size();

            for (var entry : classGraph.getTypesByJar().get(jar).entrySet()) {
                var type = entry.getKey();
                if (isIgnored(type, typeExclusions)) continue;
                var typeDef = entry.getValue();
                typeDef.type.typesReferredTo().forEach(ref -> {
                    if (isIgnored(ref, typeExclusions)) return;
                    addErrorOrUseJar(classGraph, ref, allErrors,
                            usedJars, jar,
                            () -> "Type " + ref + ", used in type signature of " + jar.getName() + "!" + type +
                                    " cannot be found"
                    );
                });
                for (var methodHandle : typeDef.usedMethodHandles) {
                    if (isIgnored(methodHandle.typeName, typeExclusions)) continue;
                    var error = errorIfMethodDoesNotExist(jar, type, null, classGraph, methodHandle, "MethodHandle");
                    addErrorOrUseJar(classGraph, methodHandle, allErrors, usedJars, error, typeExclusions);
                }
                typeDef.methods.forEach((method, codes) ->
                        checkCode(classGraph, jar, typeDef.typeName, method, codes,
                                allErrors, usedJars, typeExclusions));
                for (var field : typeDef.fields) {
                    var ref = field.type;
                    if (isIgnored(ref, typeExclusions)) continue;
                    addErrorOrUseJar(classGraph, ref, allErrors,
                            usedJars, jar, () -> "Type " + ref + ", used in field " + field.name +
                                    " of " + jar.getName() + "!" + type + " cannot be found"
                    );
                }
            }

            log.verbosePrintln(() -> "Checked " + jar.getName() + " classpath requirements in " +
                    (System.currentTimeMillis() - startTime) + " ms, ok? " + (allErrors.size() == initialErrorCount));
        }

        if (allErrors.isEmpty()) return ConsistencyCheckResult.success(usedJars);

        return ConsistencyCheckResult.failure(NonEmptyCollection.of(allErrors));
    }

    private void addErrorOrUseJar(ClassGraph classGraph,
                                  String typeName,
                                  List<ClassPathInconsistency> allErrors,
                                  Set<File> usedJars,
                                  File jar,
                                  Supplier<String> errorMessage) {
        var def = classGraph.findTypeDefinitionLocation(typeName);
        if (def == null && !classGraph.existsJava(typeName)) {
            allErrors.add(new ClassPathInconsistency(
                    errorMessage.get(), jar, classGraph.getJarByType().get(typeName)));
        }
        if (def != null) usedJars.add(def.jar);
    }

    private void addErrorOrUseJar(ClassGraph classGraph,
                                  Code code,
                                  List<ClassPathInconsistency> allErrors,
                                  Set<File> usedJars,
                                  ClassPathInconsistency error,
                                  Set<Pattern> typeExclusions) {
        if (error == null) {
            var typeDefLocation = classGraph.findTypeDefinitionLocation(code.typeName);
            assert typeDefLocation != null;
            usedJars.add(typeDefLocation.jar);
            // also check the referred method is consistent (the types are already checked)
            var nextError = code.match(NoOp.fun(),
                    NoOp.fun(),
                    m -> errorIfMethodDoesNotExist(typeDefLocation.jar, m.typeName, null,
                            classGraph, m, "Method"));
            if (nextError != null) allErrors.add(nextError);
        } else {
            allErrors.add(error);
        }
    }

    private void checkCode(ClassGraph classGraph,
                           File jarFrom,
                           String typeFrom,
                           Definition.MethodDefinition method,
                           Collection<Code> codes,
                           List<ClassPathInconsistency> allErrors,
                           Set<File> usedJars,
                           Set<Pattern> typeExclusions) {
        for (var code : codes) {
            var error = errorIfCodeRefDoesNotExist(classGraph, jarFrom, typeFrom, method, code, typeExclusions);
            addErrorOrUseJar(classGraph, code, allErrors, usedJars, error, typeExclusions);
        }
    }

    private ClassPathInconsistency errorIfCodeRefDoesNotExist(ClassGraph classGraph,
                                                              File jarFrom,
                                                              String typeFrom,
                                                              Definition.MethodDefinition methodFrom,
                                                              Code target,
                                                              Set<Pattern> typeExclusions) {
        var typeName = cleanArrayTypeName(target.typeName);
        if (isIgnored(typeName, typeExclusions)) return null;
        var error = errorIfTypeDoesNotExist(typeFrom, jarFrom, methodFrom, classGraph, typeName);
        if (error != null) return error;
        return target.match(
                NoOp.fun(),
                f -> errorIfFieldDoesNotExist(jarFrom, typeFrom, methodFrom, classGraph, f),
                m -> errorIfMethodDoesNotExist(jarFrom, typeFrom, methodFrom, classGraph, m, "Method"));
    }

    private static ClassPathInconsistency errorIfTypeDoesNotExist(String typeFrom,
                                                                  File jarFrom,
                                                                  Definition.MethodDefinition methodFrom,
                                                                  ClassGraph classGraph,
                                                                  String targetType) {

        if (!classGraph.getJarByType().containsKey(targetType) &&
                !classGraph.existsJava(targetType)) {
            return new ClassPathInconsistency("Type " + targetType +
                    ", used in method " + methodFrom.getDescription() + " of " +
                    jarFrom.getName() + "!" + typeFrom + " cannot be found in the classpath",
                    jarFrom, null);
        }
        return null;
    }

    private ClassPathInconsistency errorIfFieldDoesNotExist(File jar,
                                                            String type,
                                                            Definition.MethodDefinition methodDef,
                                                            ClassGraph classGraph,
                                                            Code.Field field) {
        var fieldOwner = field.typeName;
        var targetField = new Definition.FieldDefinition(field.name, field.type);
        var targetJar = classGraph.getJarByType().get(fieldOwner);
        if (targetJar == null) {
            return classGraph.existsJava(fieldOwner, targetField) ? null :
                    new ClassPathInconsistency("Field " + targetField.getDescription() +
                            ", used in method " + methodDef.getDescription() + " of " +
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
                    .map(Definition.FieldDefinition::getDescription)
                    .collect(joining(", ", "[", "]"));
            return "Could not find " + targetField.getDescription() + " in " + typeDef.typeName +
                    ", available fields are " + fields;
        });
        return new ClassPathInconsistency("Field " + targetField.getDescription() +
                ", used in method " + methodDef.getDescription() + " of " +
                jar.getName() + "!" + type + " cannot be found as there is no such field in " +
                targetJar + "!" + typeDef.typeName,
                jar, targetJar);
    }

    private ClassPathInconsistency errorIfMethodDoesNotExist(File jar,
                                                             String type,
                                                             Definition.MethodDefinition methodDef,
                                                             ClassGraph classGraph,
                                                             Code.Method method,
                                                             String methodKind) {
        var methodOwner = method.typeName;
        var targetMethod = method.toDefinition();
        var targetJar = classGraph.getJarByType().get(methodOwner);
        if (targetJar == null) {
            return classGraph.existsJava(methodOwner, targetMethod)
                    ? null
                    : missingMethod(type, jar, methodDef, methodKind, methodOwner, targetMethod, null);
        }
        if (jar.equals(targetJar)) return null; // do not check same-jar relations
        if (classGraph.exists(methodOwner, targetMethod)) {
            return null;
        }
        var typeDef = classGraph.getTypeDefinition(methodOwner);
        assert typeDef != null;
        log.verbosePrintln(() -> {
            var methods = typeDef.methods.keySet().stream()
                    .map(Definition.MethodDefinition::getDescription)
                    .collect(joining(", ", "[", "]"));
            return "Could not find " + targetMethod.getDescription() + " in " + typeDef.typeName +
                    ", available methods are " + methods;
        });
        return missingMethod(type, jar, methodDef, methodKind, methodOwner, targetMethod, targetJar);
    }

    private static ClassPathInconsistency missingMethod(String type,
                                                        File jar,
                                                        Definition.MethodDefinition methodDef,
                                                        String methodKind,
                                                        String methodOwner,
                                                        Definition.MethodDefinition targetMethod,
                                                        File targetJar) {
        return new ClassPathInconsistency(methodKind + " " +
                describe(targetJar, typeNameToClassName(methodOwner), targetMethod) +
                (methodDef == null ? "" : ", used in method " +
                        describe(jar, typeNameToClassName(type), methodDef)) +
                " does not exist",
                jar, targetJar);
    }

    private static String describe(File jar, String type, Definition.MethodDefinition methodDef) {
        return (jar == null ? "" : jar.getName() + '!') +
                type + '#' + methodDef.getDescription();
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

        for (var failureResult : results) {
            if (failureResult.aborted || failureResult.getErrors().isEmpty()) break;
            log.println(() -> LINE_END + "Attempted classpath: " + failureResult.jarSet.toClasspath());
            var errors = failureResult.getErrors().get();
            var errorCount = errors.stream().count();
            log.println("✗ Found " + errorCount + " error" + (errorCount == 1 ? "" : "s") + ":");
            var reportable = errorCount > 5 && !log.isVerbose()
                    ? errors.take(5)
                    : errors.toList();
            for (var error : reportable) {
                log.println("  * " + error.message);
            }
            if (reportable.size() < errorCount) {
                log.println(() -> "  ... <enable verbose logging to see all " + errorCount + " errors>" + LINE_END);
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
        final NonEmptyCollection<ClassPathInconsistency> inconsistencies;
        final Set<File> jars;

        static ConsistencyCheckResult success(Set<File> jars) {
            assert jars != null;
            return new ConsistencyCheckResult(null, jars);
        }

        static ConsistencyCheckResult failure(NonEmptyCollection<ClassPathInconsistency> inconsistencies) {
            assert inconsistencies != null;
            return new ConsistencyCheckResult(inconsistencies, null);
        }

        private ConsistencyCheckResult(NonEmptyCollection<ClassPathInconsistency> inconsistencies,
                                       Set<File> jars) {
            this.inconsistencies = inconsistencies;
            this.jars = jars;
        }

        boolean isOk() {
            return inconsistencies == null;
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

        @Override
        public String toString() {
            return "ClassPathInconsistency{" +
                    "message='" + message + '\'' +
                    ", jarFrom=" + jarFrom +
                    ", jarTo=" + jarTo +
                    '}';
        }
    }

    private static final class LimitedConcurrencyAsyncCompleter {

        private final int maxConcurrentCompletions;
        private final int totalCompletions;
        private final Deque<Supplier<CompletionStage<ClasspathCheckResult>>> completions;
        private final List<ClasspathCheckResult> results;
        private final CompletableFuture<List<ClasspathCheckResult>> futureResult;

        public LimitedConcurrencyAsyncCompleter(int maxConcurrentCompletions,
                                                List<Supplier<CompletionStage<ClasspathCheckResult>>> completions) {
            this.maxConcurrentCompletions = maxConcurrentCompletions;
            this.totalCompletions = completions.size();
            this.completions = new ConcurrentLinkedDeque<>(completions);
            this.results = new ArrayList<>(completions.size());
            this.futureResult = new CompletableFuture<>();
        }

        public CompletionStage<List<ClasspathCheckResult>> toList() {
            var aborted = new AtomicBoolean(false);
            for (var i = 0; i < maxConcurrentCompletions; i++) {
                var nextStage = completions.poll();
                if (nextStage == null) break;
                runAsync(() -> next(aborted, nextStage));
            }
            return futureResult;
        }

        private void next(AtomicBoolean aborted,
                          Supplier<CompletionStage<ClasspathCheckResult>> stageSupplier) {
            BiConsumer<ClasspathCheckResult, Throwable> onDone = (ok, err) -> {
                if (aborted.get()) return;
                synchronized (results) {
                    if (err == null) {
                        results.add(ok);
                    } else {
                        aborted.set(true);
                        futureResult.completeExceptionally(err);
                        return;
                    }
                }
                if (results.size() == totalCompletions) {
                    futureResult.complete(results);
                } else {
                    var nextStage = completions.poll();
                    if (nextStage != null) next(aborted, nextStage);
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
