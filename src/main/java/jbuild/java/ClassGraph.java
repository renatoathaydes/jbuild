package jbuild.java;

import jbuild.java.code.Code;
import jbuild.java.code.Definition;
import jbuild.java.code.TypeDefinition;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static jbuild.util.CollectionUtils.streamOfOptional;
import static jbuild.util.JavaTypeUtils.cleanArrayTypeName;
import static jbuild.util.JavaTypeUtils.isPrimitiveJavaType;
import static jbuild.util.JavaTypeUtils.toMethodTypeDescriptor;
import static jbuild.util.JavaTypeUtils.toTypeDescriptor;
import static jbuild.util.JavaTypeUtils.typeNameToClassName;

public final class ClassGraph {

    private final Map<String, Map<String, TypeDefinition>> typesByJar;
    private final Map<String, String> jarByType;

    public ClassGraph(Map<String, Map<String, TypeDefinition>> typesByJar,
                      Map<String, String> jarByType) {
        this.typesByJar = typesByJar;
        this.jarByType = jarByType;
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
        var jar = jarByType.get(code.typeName);
        if (jar == null) return Set.of();

        var visitedDefinitions = new HashSet<>();

        // start with the direct references to the type
        var result = referencesToCode(code);

        // now find all indirect references to the type via its methods and fields

        var type = typesByJar.get(jar).get(code.typeName);

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

        return result.collect(toSet());
    }

    public TypeDefinition findTypeDefinition(String typeName) {
        var jar = jarByType.get(typeName);
        if (jar != null) return typesByJar.get(jar).get(typeName);
        return null;
    }

    /**
     * Check if a certain definition exists.
     *
     * @param typeName   the type where the definition might exist
     * @param definition a definition being referenced from elsewhere
     * @return true if the definition exists in the type
     */
    public boolean exists(String typeName, Definition definition) {
        var typeDef = findTypeDefinition(typeName);
        if (typeDef == null) return existsJava(typeName, definition);
        var result = definition.match(
                typeDef.fields::contains,
                typeDef.methods::containsKey);
        if (result) return true;

        for (var parentType : typeDef.type.getParentTypes()) {
            typeName = parentType.name;
            typeDef = findTypeDefinition(typeName);
            if (typeDef == null && existsJava(typeName, definition)) return true;
        }
        return false;
    }

    public boolean existsJava(String typeName) {
        var type = cleanArrayTypeName(typeName);
        return getJavaType(type) != null || isPrimitiveJavaType(type);
    }

    public boolean existsJava(String typeName, Definition definition) {
        var type = getJavaType(typeName);
        if (type == null) return false;
        return definition.match(f -> javaFieldExists(type, f), m -> javaMethodExists(type, m));
    }

    private static Class<?> getJavaType(String typeName) {
        if (typeName.startsWith("Ljava/") || typeName.startsWith("Ljavax/") || typeName.startsWith("Lcom/sun/")) {
            try {
                return Class.forName(typeNameToClassName(typeName));
            } catch (ClassNotFoundException e) {
                // ignore
            }
        }
        return null;
    }

    private static boolean javaFieldExists(Class<?> type, Definition.FieldDefinition field) {
        for (var javaField : type.getFields()) {
            if (javaField.getName().equals(field.name)) {
                var fieldType = toTypeDescriptor(javaField.getType());
                if (fieldType.equals(field.type)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean javaMethodExists(Class<?> type, Definition.MethodDefinition method) {
        if (method.name.equals("\"<init>\"")) {
            return javaConstructorExists(type, method.type);
        }

        for (var javaMethod : type.getMethods()) {
            if (javaMethod.getName().equals(method.name)) {
                var methodType = toMethodTypeDescriptor(javaMethod.getReturnType(), javaMethod.getParameterTypes());
                if (methodType.equals(method.type)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean javaConstructorExists(Class<?> type, String constructorType) {
        for (var constructor : type.getConstructors()) {
            var typeDescriptor = toMethodTypeDescriptor(void.class, constructor.getParameterTypes());
            if (typeDescriptor.equals(constructorType)) {
                return true;
            }
        }
        return false;
    }

    private Stream<CodeReference> referencesToCode(Code code) {
        var jar = jarByType.get(code.typeName);
        if (jar == null) return Stream.of();
        return typesByJar.keySet().stream()
                .filter(j -> !j.equals(jar))
                .flatMap(j -> refs(j, code));
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

    private static Map<String, Set<String>> computeJarsByType(Map<String, Map<String, TypeDefinition>> classesByJar) {
        var jarsByClassName = new HashMap<String, Set<String>>(
                classesByJar.values().stream().mapToInt(Map::size).sum());

        for (var entry : classesByJar.entrySet()) {
            for (var type : entry.getValue().values()) {
                jarsByClassName.computeIfAbsent(type.typeName,
                        (ignore) -> new HashSet<>(2)
                ).add(entry.getKey());
            }
        }

        return Collections.unmodifiableMap(jarsByClassName);
    }
}
