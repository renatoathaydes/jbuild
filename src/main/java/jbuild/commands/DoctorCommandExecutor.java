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
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;
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

    public void run(String inputDir, boolean interactive, List<String> entryPoints) {
        var results = findClasspathPermutations(inputDir, interactive, entryPoints);
        showClasspathCheckResults(results);
    }

    public List<ClasspathCheckResult> findClasspathPermutations(String inputDir,
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
        var classGraphs = classGraphLoader.fromJars(jarFiles);

        if (classGraphs.isEmpty()) {
            throw new JBuildException("Could not find any valid classpath permutation", ACTION_ERROR);
        }

        // FIXME

        return findClasspathPermutations(classGraph, entryJars);
    }

    private static boolean includes(File jar, String entryPoint) {
        return jar.getName().equals(entryPoint) || jar.getPath().equals(entryPoint);
    }

    private void showClasspathCheckResults(List<ClasspathCheckResult> results) {
        if (results.stream().anyMatch(r -> r.successful)) {
            var successful = results.stream()
                    .filter(r -> r.successful)
                    .map(r -> r.jarByType.values())
                    .collect(toList());
            if (successful.size() == 1) {
                showSuccessfulClasspath(successful.iterator().next());
            } else {
                showSuccessfulClasspaths(successful);
            }
        } else {
            log.println("No classpath permutation could be found to satisfy all entrypoints, " +
                    "try a different classpath or entrypoint!");

            for (var result : results) {
                log.println("\nAttempted classpath: " + toClasspath(result.jarByType.values()));
                log.println("Errors:");
                for (String error : result.errors) {
                    log.println("  * " + error);
                }
                log.println("");
            }

            throw new JBuildException("No classpath permutation could be found to satisfy all entrypoints, " +
                    "try a different classpath or entrypoint", ACTION_ERROR);
        }
    }

    private List<ClasspathCheckResult> findClasspathPermutations(
            ClassGraph classGraph,
            Set<String> entryJars) {
        var jarSet = new JarSet(log, classGraph.getJarsByType());
        var jarPermutations = jarSet.computeUniqueJarSetPermutations();

        if (jarPermutations.isEmpty()) {
            throw new IllegalStateException("no jars permutations found");
        }

        var results = new ArrayList<ClasspathCheckResult>(jarPermutations.size());

        if (jarPermutations.size() > 1) {
            log.println("Detected conflicts in classpath, trying to find combinations of jars that can work together.");

            for (var jarByType : jarPermutations) {
                log.verbosePrintln(() -> "Trying classpath permutation: " + toClasspath(jarByType.values()));
                results.add(new ClasspathCheckResult(jarByType,
                        checkClasspathConsistency(jarByType, entryJars, classGraph)));
            }
        } else {
            log.println("No conflicts found in classpath.");
            var jarByType = jarPermutations.iterator().next();
            log.verbosePrintln(() -> "Trying classpath: " + toClasspath(jarByType.values()));
            results.add(new ClasspathCheckResult(jarByType,
                    checkClasspathConsistency(jarByType, entryJars, classGraph)));
        }
        return results;
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
            return List.of("Classpath permutation does not include all entry points: " + classpath);
        }
        var allErrors = new ArrayList<String>();
        jarByType.forEach((type, jar) -> {
            var typeDef = classGraph.getTypesByJar().get(jar).get(type);
            typeDef.type.typesReferredTo().forEach(ref -> {
                var typeName = cleanArrayTypeName(ref);
                if (!jarByType.containsKey(typeName) && !classGraph.existsJava(typeName)) {
                    allErrors.add("Type " + typeName + ", used in type signature of " +
                            jar + "!" + type + " cannot be found");
                }
            });
            for (var methodHandle : typeDef.methodHandles) {
                var error = errorIfMethodDoesNotExist(jarByType, type, jar, null, classGraph, methodHandle, "MethodHandle");
                if (error != null) allErrors.add(error);
            }
            typeDef.methods.forEach((method, codes) -> {
                for (var code : codes) {
                    var error = errorIfCodeRefDoesNotExist(jarByType, classGraph, type, jar, method, code);
                    if (error != null) allErrors.add(error);
                }
            });
        });

        if (log.isVerbose()) {
            if (allErrors.isEmpty()) {
                log.verbosePrintln("Classpath has no errors: " + toClasspath(classpath));
            } else {
                log.verbosePrintln("Classpath contains errors: " + toClasspath(classpath));
            }
        }

        return allErrors;
    }

    private String errorIfCodeRefDoesNotExist(Map<String, String> jarByType,
                                              ClassGraph classGraph,
                                              String type,
                                              String jar,
                                              Definition.MethodDefinition method,
                                              Code code) {
        return code.match(
                t -> errorIfTypeDoesNotExist(jarByType, type, jar, method, classGraph, t.typeName),
                f -> firstNonBlank(
                        errorIfTypeDoesNotExist(jarByType, type, jar, method, classGraph, f.typeName),
                        () -> errorIfFieldDoesNotExist(jarByType, type, jar, method, classGraph, f)),
                m -> firstNonBlank(
                        errorIfTypeDoesNotExist(jarByType, type, jar, method, classGraph, m.typeName),
                        () -> errorIfMethodDoesNotExist(jarByType, type, jar, method, classGraph, m, "Method")));
    }

    private static String errorIfTypeDoesNotExist(Map<String, String> jarByType,
                                                  String type,
                                                  String jar,
                                                  Definition.MethodDefinition methodDef,
                                                  ClassGraph classGraph,
                                                  String targetType) {
        var typeName = cleanArrayTypeName(targetType);
        if (!jarByType.containsKey(typeName) && !classGraph.existsJava(typeName)) {
            return "Type " + typeName + ", used in method " + methodDef.descriptor() + " of " +
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
        var fieldOwner = field.typeName;
        var targetJar = jarByType.get(fieldOwner);
        if (jar.equals(targetJar)) return null; // do not check same-jar relations
        var targetField = new Definition.FieldDefinition(field.name, field.type);
        if (targetJar == null) {
            log.verbosePrintln(() -> "Field type owner " + fieldOwner +
                    " not found in any jar, checking if it is a Java API");
            return classGraph.existsJava(fieldOwner, targetField) ? null :
                    "Field " + targetField.descriptor() + ", used in method " + methodDef.descriptor() + " of " +
                            jar + "!" + type + " cannot be found as there is no such field in " + fieldOwner;
        }
        var tDef = classGraph.getTypesByJar().get(targetJar).get(fieldOwner);
        if (classGraph.exists(targetJar, tDef, targetField)) {
            return null;
        }
        log.verbosePrintln(() -> {
            var fields = tDef.fields.stream()
                    .map(Definition.FieldDefinition::descriptor)
                    .collect(joining(", ", "[", "]"));
            return "Could not find " + targetField.descriptor() + " in " + tDef.typeName +
                    ", available fields are " + fields;
        });
        return "Field " + targetField.descriptor() + ", used in method " + methodDef.descriptor() + " of " +
                jar + "!" + type + " cannot be found as there is no such field in " +
                targetJar + "!" + tDef.typeName;
    }

    private String errorIfMethodDoesNotExist(Map<String, String> jarByType,
                                             String type,
                                             String jar,
                                             Definition.MethodDefinition methodDef,
                                             ClassGraph classGraph,
                                             Code.Method method,
                                             String methodKind) {
        var methodOwner = method.typeName;
        var targetJar = jarByType.get(methodOwner);
        if (jar.equals(targetJar)) return null; // do not check same-jar relations
        var targetMethod = new Definition.MethodDefinition(method.name, method.type);
        if (targetJar == null) {
            log.verbosePrintln(() -> "Method type owner " + methodOwner +
                    " not found in any jar, checking if it is a Java API");
            return classGraph.existsJava(methodOwner, targetMethod) ? null :
                    methodKind + " " + targetMethod.descriptor() + ", used in " +
                            (methodDef == null ? "" : " method " + methodDef.descriptor() + " of ") +
                            jar + "!" + type + " cannot be found as there is no such method in " + methodOwner;
        }
        var tDef = classGraph.getTypesByJar().get(targetJar).get(methodOwner);
        if (classGraph.exists(targetJar, tDef, targetMethod)) {
            return null;
        }
        log.verbosePrintln(() -> {
            var methods = tDef.methods.keySet().stream()
                    .map(Definition.MethodDefinition::descriptor)
                    .collect(joining(", ", "[", "]"));
            return "Could not find " + targetMethod.descriptor() + " in " + tDef.typeName +
                    ", available methods are " + methods;
        });
        return methodKind + " " + targetMethod.descriptor() + ", used in " +
                (methodDef == null ? "" : " method " + methodDef.descriptor() + " of ") +
                jar + "!" + type + " cannot be found as there is no such method in " +
                targetJar + "!" + tDef.typeName;
    }

    private static String toClasspath(Collection<String> jars) {
        return String.join(File.pathSeparator, new HashSet<>(jars));
    }

    public static final class ClasspathCheckResult {

        public final List<String> errors;
        public final Map<String, String> jarByType;
        public final boolean successful;

        public ClasspathCheckResult(Map<String, String> jarByType, List<String> errors) {
            this.jarByType = jarByType;
            this.errors = errors;
            successful = errors.isEmpty();
        }
    }
}
