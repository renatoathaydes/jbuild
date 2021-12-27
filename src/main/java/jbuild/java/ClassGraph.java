package jbuild.java;

import jbuild.java.code.Code;
import jbuild.java.code.TypeDefinition;
import jbuild.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static jbuild.util.CollectionUtils.streamOfOptional;

public final class ClassGraph {

    private final Map<String, Map<String, TypeDefinition>> classesByJar;
    private final Map<String, List<String>> jarsByType;

    public ClassGraph(Map<String, Map<String, TypeDefinition>> classesByJar) {
        this.classesByJar = classesByJar;
        this.jarsByType = computeJarsByType(classesByJar);
    }

    public Map<String, List<String>> getJarsByType() {
        return jarsByType;
    }

    /**
     * Get direct references to a {@link Code.Method} value from jars other than the ones where this code is defined.
     *
     * @param code value
     * @return direct references to the code, excluding references from the same jar where the code is defined
     */
    public Set<CodeReference> referencesTo(Code.Method code) {
        return referencesToCode(code)
                .collect(toSet());
    }

    /**
     * Get direct references to a {@link Code.Field} value from jars other than the ones where this code is defined.
     *
     * @param code value
     * @return direct references to the code, excluding references from the same jar where the code is defined
     */
    public Set<CodeReference> referencesTo(Code.Field code) {
        return referencesToCode(code)
                .collect(toSet());
    }

    /**
     * Get all references to a {@link Code.Type} value from jars other than the ones where this code is defined.
     * <p>
     * References to the type's methods and fields are also included in the result.
     *
     * @param code value
     * @return references to the code, excluding references from the same jar where the code is defined
     */
    public Set<CodeReference> referencesTo(Code.Type code) {
        var selfJars = jarsByType.get(code.typeName);
        if (selfJars == null || selfJars.isEmpty()) return Set.of();

        var visitedDefinitions = new HashSet<>();

        // start with the direct references to the type
        var result = referencesToCode(code);

        // now find all indirect references to the type via its methods and fields
        for (var selfJar : selfJars) {
            var type = classesByJar.get(selfJar).get(code.typeName);

            for (var field : type.fields) {
                var isNew = visitedDefinitions.add(field);
                if (isNew) {
                    result = Stream.concat(result,
                            referencesToCode(new Code.Field(type.typeName, field.name, field.type)));
                }
            }

            for (var method : type.methods.keySet()) {
                var isNew = visitedDefinitions.add(method);
                if (isNew) {
                    // references to constructors are via the "<init>" name, not the type name
                    var methodName = method.isConstructor() ? "\"<init>\"" : method.name;
                    result = Stream.concat(result,
                            referencesToCode(new Code.Method(type.typeName, methodName, method.type)));
                }
            }

            for (var methodHandle : type.methodHandles) {
                var isNew = visitedDefinitions.add(methodHandle);
                if (isNew) {
                    result = Stream.concat(result, referencesToCode(methodHandle));
                }
            }
        }

        return result.collect(toSet());
    }

    private Stream<CodeReference> referencesToCode(Code code) {
        var selfJars = jarsByType.get(code.typeName);
        if (selfJars == null || selfJars.isEmpty()) return Stream.of();
        var otherJars = CollectionUtils.difference(classesByJar.keySet(), selfJars);
        return otherJars.stream()
                .flatMap(jar -> refs(jar, code));
    }

    private Stream<CodeReference> refs(String jarFrom, Code to) {
        return classesByJar.get(jarFrom).values().stream()
                .flatMap(type -> refs(jarFrom, type, to));
    }

    private Stream<CodeReference> refs(String jarFrom, TypeDefinition typeFrom, Code to) {
        var fromMethodsAndHandles = Stream.concat(
                typeFrom.methodHandles.stream()
                        .filter(to::equals)
                        .map(code -> new CodeReference(jarFrom, typeFrom.typeName, null, to)),
                typeFrom.methods.entrySet().stream()
                        .flatMap(entry -> streamOfOptional(entry.getValue().stream()
                                .filter(to::equals)
                                .findAny()
                                .map(code -> new CodeReference(jarFrom, typeFrom.typeName, entry.getKey(), to)))));

        // find references to a type in the type signature of the methods, even when the type is not used
        if (to instanceof Code.Type) {
            var fromMethodSignatures = typeFrom.methods.keySet().stream()
                    .filter(method -> method.getReturnType().equals(to.typeName) ||
                            method.getParameterTypes().contains(to.typeName))
                    .map(method -> new CodeReference(jarFrom, typeFrom.typeName, method, to));

            if (typeFrom.implementedInterfaces.contains(to.typeName)) {
                return Stream.concat(
                        Stream.of(new CodeReference(jarFrom, typeFrom.typeName, null, to)),
                        Stream.concat(fromMethodsAndHandles, fromMethodSignatures));
            }

            return Stream.concat(fromMethodsAndHandles, fromMethodSignatures);
        }

        return fromMethodsAndHandles;
    }

    private static Map<String, List<String>> computeJarsByType(Map<String, Map<String, TypeDefinition>> classesByJar) {
        var jarsByClassName = new HashMap<String, List<String>>(
                classesByJar.values().stream().mapToInt(Map::size).sum());

        for (var entry : classesByJar.entrySet()) {
            for (var type : entry.getValue().values()) {
                jarsByClassName.computeIfAbsent(type.typeName,
                        (ignore) -> new ArrayList<>(2)
                ).add(entry.getKey());
            }
        }

        return Collections.unmodifiableMap(jarsByClassName);
    }
}
