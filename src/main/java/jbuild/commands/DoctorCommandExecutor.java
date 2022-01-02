package jbuild.commands;

import jbuild.errors.JBuildException;
import jbuild.java.ClassGraph;
import jbuild.java.ClassGraphLoader;
import jbuild.java.JarSet;
import jbuild.java.code.Code;
import jbuild.java.code.Definition;
import jbuild.log.JBuildLog;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;
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

    public void run(String inputDir, boolean interactive, List<String> entryPoints) {
        var classGraph = classGraphLoader.fromJarsInDirectory(inputDir);
        var entryJars = classGraph.getTypesByJar().keySet().stream()
                .filter(entryPoints::contains)
                .collect(toSet());
        if (entryJars.isEmpty()) {
            throw new JBuildException("Could not find any entry point", USER_INPUT);
        }
        checkAllEntryPointsAreFullySatisfied(classGraph, entryJars);
    }

    private void checkAllEntryPointsAreFullySatisfied(ClassGraph classGraph,
                                                      Set<String> entryJars) {
        var jarSet = new JarSet(classGraph.getJarsByType());
        var jarPermutations = jarSet.computeUniqueJarSetPermutations();

        if (jarPermutations.isEmpty()) {
            throw new IllegalStateException("no jars permutations found");
        }
        if (jarPermutations.size() > 1) {
            log.println("Detected conflicts in classpath, trying to find combinations of jars that can work together");

            var results = new ArrayList<ClasspathCheckResult>(jarPermutations.size());
            for (var permutation : jarPermutations) {
                log.verbosePrintln(() -> "Trying classpath permutation: " + toClasspath(permutation.values()));
                results.add(new ClasspathCheckResult(permutation,
                        checkClasspathConsistency(permutation, entryJars, classGraph)));
            }

            if (results.stream().anyMatch(r -> r.successful)) {
                var successful = results.stream()
                        .filter(r -> r.successful)
                        .map(r -> r.permutation.values())
                        .collect(toList());
                if (successful.size() == 1) {
                    showSuccessfulClasspath(successful.iterator().next());
                } else {
                    showSuccessfulClasspaths(successful);
                }
            } else {
                log.println("No classpath permutation could be found to satisfy all entrypoints, " +
                        "try a different classpath or entrypoint!");

                for (ClasspathCheckResult result : results) {
                    log.println("\nAttempted classpath: " + toClasspath(result.permutation.values()));
                    log.println("Errors:");
                    for (String error : result.errors) {
                        log.println("  * " + error);
                    }
                    log.println("");
                }

                throw new JBuildException("No classpath permutation could be found to satisfy all entrypoints, " +
                        "try a different classpath or entrypoint", ACTION_ERROR);
            }
        } else {
            log.println("No conflicts found in classpath.");
            var jarByType = jarPermutations.iterator().next();
            var errors = checkClasspathConsistency(jarByType, entryJars, classGraph);
            if (!errors.isEmpty()) {
                throw new JBuildException("The classpath is not consistent: " + errors, ACTION_ERROR);
            }
            showSuccessfulClasspath(jarByType.values());
        }
    }

    private void showSuccessfulClasspath(Collection<String> jars) {
        log.println("All entrypoint type dependencies are satisfied by the classpath below:\n");
        log.println(toClasspath(jars));
    }

    private void showSuccessfulClasspaths(Collection<? extends Collection<String>> classpaths) {
        log.println("All entrypoint type dependencies are satisfied by the following classpaths:\n");
        for (Collection<String> classpath : classpaths) {
            log.println(toClasspath(classpath));
            log.println("");
        }
    }

    private List<String> checkClasspathConsistency(Map<String, String> jarByType,
                                                   Set<String> entryJars,
                                                   ClassGraph classGraph) {
        var classpath = new HashSet<>(jarByType.values());
        if (!classpath.containsAll(entryJars)) {
            return List.of("Permutation does not include all entry points: " + classpath);
        }
        var allErrors = new ArrayList<String>();
        jarByType.forEach((type, jar) -> {
            var typeDef = classGraph.getTypesByJar().get(jar).get(type);
            typeDef.type.typesReferredTo().forEach(ref -> {
                if (!jarByType.containsKey(ref)) {
                    allErrors.add("Type " + ref + ", used in type signature of " +
                            jar + "!" + type + " cannot be found");
                }
            });
            for (var methodHandle : typeDef.methodHandles) {
                var error = errorIfMethodHandleDoesNotExist(jarByType, classGraph, type, jar, methodHandle);
                if (error != null) allErrors.add(error);
            }
            typeDef.methods.forEach((method, codes) -> {
                for (var code : codes) {
                    var error = errorIfCodeRefDoesNotExist(jarByType, classGraph, type, jar, method, code);
                    if (error != null) allErrors.add(error);
                }
            });
        });

        return allErrors;
    }

    private String errorIfCodeRefDoesNotExist(Map<String, String> jarByType,
                                              ClassGraph classGraph,
                                              String type,
                                              String jar,
                                              Definition.MethodDefinition method,
                                              Code code) {
        return code.match(
                t -> errorIfTypeDoesNotExist(jarByType, type, jar, method, t.typeName),
                field -> firstNonBlank(
                        errorIfTypeDoesNotExist(jarByType, type, jar, method, field.typeName),
                        () -> errorIfFieldDoesNotExist(jarByType, type, jar, method, classGraph, field)),
                m -> firstNonBlank(
                        errorIfTypeDoesNotExist(jarByType, type, jar, method, m.typeName),
                        () -> errorIfMethodDoesNotExist(jarByType, type, jar, method, classGraph, m)));
    }

    private static String errorIfTypeDoesNotExist(Map<String, String> jarByType,
                                                  String type,
                                                  String jar,
                                                  Definition.MethodDefinition methodDef,
                                                  String targetType) {
        if (targetType.startsWith("Ljava") && !jarByType.containsKey(targetType)) {
            return "Type " + targetType + ", used in method " + methodDef.descriptor() + " of " +
                    jar + "!" + type + " cannot be found in the classpath";
        }
        return null;
    }

    private String errorIfFieldDoesNotExist(Map<String, String> jarByType,
                                            String type,
                                            String jar,
                                            Definition.MethodDefinition methodDef,
                                            ClassGraph classGraph,
                                            Code.Field field) {
        var targetJar = jarByType.get(field.typeName);
        if (targetJar.equals(jar)) return null; // do not check same-jar relations
        var tDef = classGraph.getTypesByJar().get(targetJar).get(field.typeName);
        var targetField = new Definition.FieldDefinition(field.name, field.type);
        if (classGraph.exists(targetJar, tDef, targetField)) {
            return null;
        }
        return "Field " + targetField.descriptor() + ", used in method " + methodDef.descriptor() + " of " +
                jar + "!" + type + " cannot be found as there is no such field in " +
                targetJar + "!" + tDef.typeName;
    }

    private String errorIfMethodDoesNotExist(Map<String, String> jarByType,
                                             String type,
                                             String jar,
                                             Definition.MethodDefinition methodDef,
                                             ClassGraph classGraph,
                                             Code.Method method) {
        var targetJar = jarByType.get(method.typeName);
        if (targetJar.equals(jar)) return null; // do not check same-jar relations
        var tDef = classGraph.getTypesByJar().get(targetJar).get(method.typeName);
        var targetMethod = new Definition.MethodDefinition(method.name, method.type);
        if (classGraph.exists(targetJar, tDef, targetMethod)) {
            return null;
        }
        return "Method " + targetMethod.descriptor() + ", used in method " + methodDef.descriptor() + " of " +
                jar + "!" + type + " cannot be found as there is no such method in " +
                targetJar + "!" + tDef.typeName;
    }

    private static String errorIfMethodHandleDoesNotExist(Map<String, String> jarByType,
                                                          ClassGraph classGraph,
                                                          String type,
                                                          String jar,
                                                          Code.Method methodHandle) {
        var targetJar = jarByType.get(methodHandle.typeName);
        var methodDef = new Definition.MethodDefinition(methodHandle.name, methodHandle.type);
        if (targetJar == null) {
            return "MethodHandle " + methodDef.descriptor() + ", used in " + jar + "!" + type +
                    " cannot be found as type " + methodHandle.typeName + " is not in the classpath";
        } else {
            if (targetJar.equals(jar)) return null; // do not check same-jar relations
            var targetType = classGraph.getTypesByJar().get(targetJar).get(methodHandle.typeName);
            if (!classGraph.exists(targetJar, targetType, methodDef)) {
                return "MethodHandle " + methodDef.descriptor() + ", used in " + jar + "!" + type +
                        " cannot be found as there is no such method in " + targetJar + "!" + targetType.typeName;
            }
        }
        return null;
    }

    private static String toClasspath(Collection<String> jars) {
        return String.join(File.pathSeparator, new HashSet<>(jars));
    }

    private static final class ClasspathCheckResult {

        final List<String> errors;
        final Map<String, String> permutation;
        final boolean successful;

        public ClasspathCheckResult(Map<String, String> permutation, List<String> errors) {
            this.permutation = permutation;
            this.errors = errors;
            successful = errors.isEmpty();
        }
    }
}
