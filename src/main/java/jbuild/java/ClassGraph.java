package jbuild.java;

import jbuild.java.code.Code;
import jbuild.java.code.Definition;
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

    private final Map<String, Map<String, TypeDefinition>> typesByJar;
    private final Map<String, List<String>> jarsByType;

    public ClassGraph(Map<String, Map<String, TypeDefinition>> typesByJar) {
        this.typesByJar = typesByJar;
        this.jarsByType = computeJarsByType(typesByJar);
    }

    /**
     * @return a Map from all types in this graph to the jar(s) in which they can be found.
     */
    public Map<String, List<String>> getJarsByType() {
        return jarsByType;
    }

    /**
     * @return a Map from all jars in this graph to the types which they contain, indexed by name.
     */
    public Map<String, Map<String, TypeDefinition>> getTypesByJar() {
        return typesByJar;
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
            var type = typesByJar.get(selfJar).get(code.typeName);

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
        }

        return result.collect(toSet());
    }

    /**
     * Check if a certain reference to a definition on the given type, in the given jar, exists.
     *
     * @param jar        where the type is located
     * @param typeDef    the type where the definition might exist
     * @param definition a definition being referenced from elsewhere
     * @return true if the definition exists in the type
     */
    public boolean refExists(String jar, TypeDefinition typeDef, Definition definition) {
        var result = definition.match(
                typeDef.fields::contains,
                typeDef.methods::containsKey);
        if (result) return true;

        for (var parentType : typeDef.type.getParentTypes()) {
            var typeName = parentType.name;
            var jars = jarsByType.get(typeName);
            if (jars == null) return false;
            if (jars.contains(jar)) { // prefer to find parent type on the same jar
                var t = typesByJar.get(jar).get(typeName);
                return refExists(jar, t, definition);
            } else {
                for (var currentJar : jars) {
                    var t = typesByJar.get(currentJar).get(typeName);
                    if (refExists(currentJar, t, definition)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Stream<CodeReference> referencesToCode(Code code) {
        var selfJars = jarsByType.get(code.typeName);
        if (selfJars == null || selfJars.isEmpty()) return Stream.of();
        var otherJars = CollectionUtils.difference(typesByJar.keySet(), selfJars);
        return otherJars.stream()
                .flatMap(jar -> refs(jar, code));
    }

    private Stream<CodeReference> refs(String jarFrom, Code to) {
        return typesByJar.get(jarFrom).values().stream()
                .flatMap(type -> refs(jarFrom, type, to));
    }

    private Stream<CodeReference> refs(String jarFrom, TypeDefinition typeFrom, Code to) {
        var results = Stream.concat(
                typeFrom.methodHandles.stream()
                        .filter(to::equals)
                        .map(code -> new CodeReference(jarFrom, typeFrom.typeName, null, to)),
                typeFrom.methods.entrySet().stream()
                        .flatMap(entry -> streamOfOptional(entry.getValue().stream()
                                .filter(to::equals)
                                .findAny()
                                .map(code -> new CodeReference(jarFrom, typeFrom.typeName, entry.getKey(), to)))));

        // find references to a type in fields and type signatures, even when the type is not used in code
        if (to instanceof Code.Type) {
            results = Stream.concat(
                    results,
                    typeFrom.methods.keySet().stream()
                            .filter(method -> method.getReturnType().equals(to.typeName) ||
                                    method.getParameterTypes().contains(to.typeName))
                            .map(method -> new CodeReference(jarFrom, typeFrom.typeName, method, to)));

            results = Stream.concat(
                    results,
                    typeFrom.fields.stream()
                            .filter(field -> field.type.equals(to.typeName))
                            .map(field -> new CodeReference(jarFrom, typeFrom.typeName, field, to)));

            if (typeFrom.type.typesReferredTo().anyMatch(to.typeName::equals)) {
                results = Stream.concat(
                        results,
                        Stream.of(new CodeReference(jarFrom, typeFrom.typeName, null, to)));
            }
        }

        return results;
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
