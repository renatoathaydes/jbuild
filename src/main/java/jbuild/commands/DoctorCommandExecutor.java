package jbuild.commands;

import jbuild.api.JBuildException;
import jbuild.classes.model.ClassFile;
import jbuild.classes.model.info.Reference;
import jbuild.java.ClassGraph;
import jbuild.java.JarSet;
import jbuild.java.JarSetPermutations;
import jbuild.log.JBuildLog;
import jbuild.util.CollectionUtils;
import jbuild.util.JarFileFilter;
import jbuild.util.JavaTypeUtils;
import jbuild.util.NonEmptyCollection;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static jbuild.api.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.api.JBuildException.ErrorCause.USER_INPUT;
import static jbuild.util.AsyncUtils.awaitValues;
import static jbuild.util.FileUtils.allFilesInDir;
import static jbuild.util.JavaTypeUtils.parseTypeDescriptor;
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

            var checkResults = jarSets.stream().map(jarSet -> jarSet.toClassGraph()
                    .thenApplyAsync(cg -> checkForInconsistencies(cg, entryJars, typeExclusions))
                    .thenApplyAsync(results -> ClasspathCheckResult.of(results, jarSet))
            ).collect(toList());

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

    private ConsistencyCheckResult checkForInconsistencies(
            ClassGraph classGraph,
            Set<File> entryPoints,
            Set<Pattern> typeExclusions) {
        var visitedJars = new HashSet<>(entryPoints);
        var capacity = classGraph.getTypesByJar().values().stream().mapToInt(Map::size).sum();
        var typesToVisit = new HashMap<String, ClassGraph.TypeDefinitionLocation>(capacity);
        // we will visit types referred to by the entry points
        var visitedTypes = new HashSet<String>(capacity * 2);
        var inconsistencies = new ArrayList<ClassPathInconsistency>();
        for (var jar : entryPoints) {
            classGraph.getTypesByJar().get(jar).forEach((name, type) ->
                    typesToVisit.put(name, new ClassGraph.TypeDefinitionLocation(type, jar)));
        }
        while (!typesToVisit.isEmpty()) {
            log.verbosePrintln(() -> "Checking " + typesToVisit.size() + " in this iteration, " +
                    visitedTypes.size() + " visited so far");
            var currentTypesToVisit = new HashSet<>(typesToVisit.entrySet());
            typesToVisit.clear();
            for (var typeByName : currentTypesToVisit) {
                var fromTypeName = typeByName.getKey();
                if (!visitedTypes.add(fromTypeName)) {
                    continue;
                }
                var from = typeByName.getValue();
                for (var typeRef : classGraph.getTypesReferredToBy(from.typeName)) {
                    if (JavaTypeUtils.isPrimitiveJavaType(typeRef)
                            || JavaTypeUtils.mayBeJavaStdLibType(typeRef)) {
                        continue;
                    }
                    var to = JavaTypeUtils.typeNameToClassName(typeRef);
                    if (isExcluded(to, typeExclusions)) {
                        continue;
                    }
                    var ref = classGraph.findTypeDefinitionLocation(typeRef);
                    if (ref == null) {
                        log.verbosePrintln(() -> "Type " + from + " needs missing type: " + to);
                        inconsistencies.add(new ClassPathInconsistency(refChain(from), to, ReferenceTarget.TYPE));
                    } else {
                        if (visitedJars.add(ref.jar)) {
                            log.verbosePrintln(() -> "Including jar " + ref.jar + " due to reference to " +
                                    to + " from " + from);
                        }
                        var refWithParent = ref.withParent(from);
                        typesToVisit.put(typeRef, refWithParent);
                        checkReferences(classGraph, refWithParent, typeExclusions, typesToVisit, inconsistencies);
                    }
                }
            }
        }
        log.verbosePrintln(() -> "Visited " + visitedTypes.size() + " types from " + visitedJars.size() + " jars in total");
        if (inconsistencies.isEmpty()) {
            return ConsistencyCheckResult.success(visitedJars);
        }
        return ConsistencyCheckResult.failure(NonEmptyCollection.of(inconsistencies));
    }

    private void checkReferences(
            ClassGraph classGraph,
            ClassGraph.TypeDefinitionLocation location,
            Set<Pattern> typeExclusions,
            Map<String, ClassGraph.TypeDefinitionLocation> typesToVisit,
            List<ClassPathInconsistency> results) {
        for (var ref : location.typeDefinition.classFile.getReferences()) {
            var ownerTypeInfo = JavaTypeUtils.TypeInfo.from(ref.ownerType);
            if (ownerTypeInfo.arrayDimensions > 0) {
                checkArrayReference(ownerTypeInfo, classGraph, location, ref, typesToVisit, results);
                continue;
            }
            if (!ownerTypeInfo.isReferenceType
                    || JavaTypeUtils.mayBeJavaStdLibType(ownerTypeInfo.basicTypeName)
                    || location.typeName.equals(ownerTypeInfo.basicTypeName)) {
                // skip Java stdlib types and refs to internal methods/fields
                continue;
            }
            var toClassName = JavaTypeUtils.typeNameToClassName(ownerTypeInfo);
            if (isExcluded(toClassName, typeExclusions)) {
                continue;
            }
            log.verbosePrintln(() -> "Checking reference from " + location.className + " to " +
                    (ref.kind == Reference.RefKind.FIELD ? "field " : "method ") + toClassName + "::" + ref.name +
                    " with type " + ref.descriptor);
            var toLocation = classGraph.findTypeDefinitionLocation(ownerTypeInfo.basicTypeName);
            if (toLocation == null) {
                // missing types are reported already from the ClassInfo constants
                return;
            }
            var targetLocation = toLocation.withParent(location);
            typesToVisit.put(ref.ownerType, targetLocation);
            var referenceTarget = ReferenceTarget.of(ref);
            var targetDescriptor = ref.descriptor;
            var ok = findTypeDescriptorsByName(ref, targetLocation, classGraph)
                    .anyMatch(targetDescriptor::equals);
            if (!ok) {
                var to = describe(targetLocation, ref, referenceTarget);
                log.verbosePrintln(() -> "Type " + location.className + " needs missing '" + to +
                        "' with type " + ref.descriptor);
                results.add(new ClassPathInconsistency(refChain(location), to, referenceTarget));
            }
        }
    }

    private void checkArrayReference(JavaTypeUtils.TypeInfo ownerTypeInfo,
                                     ClassGraph classGraph,
                                     ClassGraph.TypeDefinitionLocation location, Reference ref,
                                     Map<String, ClassGraph.TypeDefinitionLocation> typesToVisit,
                                     List<ClassPathInconsistency> results) {
        assert ownerTypeInfo.arrayDimensions > 0;
        if (ownerTypeInfo.isReferenceType) {
            var type = classGraph.findTypeDefinitionLocation(ownerTypeInfo.basicTypeName);
            if (type != null) {
                typesToVisit.put(ownerTypeInfo.basicTypeName, type);
            }
        }
        Stream<String> existingDescriptors;
        if (ref.kind == Reference.RefKind.FIELD) {
            existingDescriptors = JavaDescriptorsCache.findArrayFieldDescriptorsByName(ref.name);
        } else {
            existingDescriptors = JavaDescriptorsCache.findArrayMethodDescriptorsByName(ref.name);
        }
        if (existingDescriptors.noneMatch(ref.descriptor::equals)) {
            var referenceTarget = ReferenceTarget.of(ref);
            var to = JavaTypeUtils.typeNameToClassName(ownerTypeInfo);
            log.verbosePrintln(() -> "Type " + location.className + " needs missing '" + to + "::" + ref.name +
                    "' with type " + ref.descriptor);
            results.add(new ClassPathInconsistency(refChain(location), to, referenceTarget));
        }
    }

    private static String describe(ClassGraph.TypeDefinitionLocation location,
                                   Reference reference,
                                   ReferenceTarget referenceTarget) {
        var types = parseTypeDescriptor(reference.descriptor, false).stream()
                .map(JavaTypeUtils::typeNameToClassName)
                .collect(toCollection(ArrayList::new));
        if (referenceTarget == ReferenceTarget.CONSTRUCTOR) {
            // ignore the constructor's return type which is always void
            if (!types.isEmpty()) {
                types.remove(types.size() - 1);
            }
            return location + "::(" + String.join(", ", types) + ')';
        }
        if (referenceTarget == ReferenceTarget.FIELD) {
            assert types.size() == 1;
            return location + "::" + reference.name + ':' + types.get(0);
        }
        var returnType = types.remove(types.size() - 1);
        return location + "::" + reference.name + '(' + String.join(", ", types) + "):" + returnType;
    }

    private static Stream<String> findTypeDescriptorsByName(Reference reference,
                                                            ClassGraph.TypeDefinitionLocation location,
                                                            ClassGraph classGraph) {
        var targetName = reference.name;
        var classFile = location.typeDefinition.classFile;
        Set<ClassFile> parentTypes = JavaDescriptorsCache.expandWithSuperTypes(classFile, classGraph);
        if (reference.kind == Reference.RefKind.FIELD) {
            return Stream.concat(parentTypes.stream()
                            .flatMap(cf -> cf.getFields().stream()
                                    .filter(m -> m.name.equals(targetName))
                                    .map(m -> m.descriptor)),
                    JavaDescriptorsCache.findFieldDescriptorsByName(classFile, targetName, classGraph));
        }

        return Stream.concat(parentTypes.stream()
                        .flatMap(cf -> cf.getMethods().stream()
                                .filter(m -> m.name.equals(targetName))
                                .map(m -> m.descriptor)),
                JavaDescriptorsCache.findMethodDescriptorsByName(parentTypes, targetName, classGraph));
    }

    private boolean isExcluded(String className, Set<Pattern> typeExclusions) {
        for (var pattern : typeExclusions) {
            if (pattern.matcher(className).matches()) {
                log.verbosePrintln(() -> "Skipping " + className +
                        " as it matches exclusion pattern " + pattern);
                return true;
            }
        }
        return false;
    }

    private static String refChain(ClassGraph.TypeDefinitionLocation from) {
        var chain = new ArrayList<String>();
        // build the chain in reverse
        var current = from;
        while (current != null) {
            chain.add(current.jar.getName() + '!' + current.className);
            current = current.parent;
        }
        Collections.reverse(chain);
        return String.join(" -> ", chain);
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
            if (failureResult.getErrors().isEmpty()) break;
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
                problems.forEach(problem -> log.println("    - " + problem.referenceChain));
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
        public final JarSet jarSet;
        public final boolean successful;

        public ClasspathCheckResult(JarSet jarSet,
                                    NonEmptyCollection<ClassPathInconsistency> errors) {
            this.jarSet = jarSet;
            this.errors = errors;
            successful = errors == null;
        }

        private static ClasspathCheckResult of(ConsistencyCheckResult result, JarSet jarSet) {
            if (result.isOk()) {
                return new ClasspathCheckResult(jarSet.filterFiles(result.visitedJars), null);
            }
            return new ClasspathCheckResult(jarSet, result.inconsistencies);
        }

        public Optional<NonEmptyCollection<ClassPathInconsistency>> getErrors() {
            return Optional.ofNullable(errors);
        }

        @Override
        public String toString() {
            return "ClasspathCheckResult{" +
                    "errors=" + errors +
                    ", jarSet=" + jarSet +
                    ", successful=" + successful +
                    '}';
        }
    }

    private static final class ConsistencyCheckResult {

        public final Set<File> visitedJars;
        public final NonEmptyCollection<ClassPathInconsistency> inconsistencies;

        ConsistencyCheckResult(
                Set<File> visitedJars,
                NonEmptyCollection<ClassPathInconsistency> inconsistencies) {
            this.visitedJars = visitedJars;
            this.inconsistencies = inconsistencies;
        }

        static ConsistencyCheckResult success(Set<File> visitedJars) {
            return new ConsistencyCheckResult(requireNonNull(visitedJars), null);
        }

        static ConsistencyCheckResult failure(NonEmptyCollection<ClassPathInconsistency> inconsistencies) {
            return new ConsistencyCheckResult(Set.of(), requireNonNull(inconsistencies));
        }

        boolean isOk() {
            return inconsistencies == null;
        }
    }

    public enum ReferenceTarget {
        TYPE, FIELD, METHOD, CONSTRUCTOR;

        public static ReferenceTarget of(Reference ref) {
            if (ref.kind == Reference.RefKind.FIELD) {
                return FIELD;
            }
            if (ref.name.equals("<init>")) {
                return CONSTRUCTOR;
            }
            return METHOD;
        }
    }

    public static final class ClassPathInconsistency {

        public final String referenceChain;
        // Java field/method/class?
        public final String to;
        public final ReferenceTarget target;

        public ClassPathInconsistency(
                String referenceChain,
                String to,
                ReferenceTarget target) {
            this.referenceChain = referenceChain;
            this.to = to;
            this.target = target;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (ClassPathInconsistency) obj;
            return Objects.equals(this.referenceChain, that.referenceChain) &&
                    Objects.equals(this.to, that.to) &&
                    Objects.equals(this.target, that.target);
        }

        @Override
        public int hashCode() {
            return Objects.hash(referenceChain, to, target);
        }

        @Override
        public String toString() {
            return "ClassPathInconsistency{" +
                    "referenceChain='" + referenceChain + '\'' +
                    ", to='" + to + '\'' +
                    ", target=" + target +
                    '}';
        }
    }

}
