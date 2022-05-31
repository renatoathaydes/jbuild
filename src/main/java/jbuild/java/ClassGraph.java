package jbuild.java;

import jbuild.java.code.Code;
import jbuild.java.code.Definition;
import jbuild.java.code.TypeDefinition;
import jbuild.util.Describable;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static jbuild.util.JavaTypeUtils.cleanArrayTypeName;
import static jbuild.util.JavaTypeUtils.isPrimitiveJavaType;
import static jbuild.util.JavaTypeUtils.mayBeJavaStdLibType;
import static jbuild.util.JavaTypeUtils.toMethodTypeDescriptor;
import static jbuild.util.JavaTypeUtils.toTypeDescriptor;
import static jbuild.util.JavaTypeUtils.typeNameToClassName;

/**
 * A graph of types within a consistent classpath where each type is only found within a single jar.
 * <p>
 * A {@link ClassGraph} can be used to check the validity of a Java classpath without having to wait for runtime
 * errors such as {@link NoClassDefFoundError}.
 * <p>
 * It can find references to Java definitions (types, fields, methods) from other jars and answer whether a certain
 * definition exists, for example.
 * <p>
 * This class uses the internal JVM type descriptors for all type names.
 */
public final class ClassGraph {

    private static final Definition.FieldDefinition ARRAY_LENGTH =
            new Definition.FieldDefinition("length", "I");

    private static final Definition.MethodDefinition ARRAY_CLONE =
            new Definition.MethodDefinition("clone", "()Ljava/lang/Object;");

    private final Map<File, Map<String, TypeDefinition>> typesByJar;
    private final Map<String, File> jarByType;

    public ClassGraph(Map<File, Map<String, TypeDefinition>> typesByJar,
                      Map<String, File> jarByType) {
        this.typesByJar = typesByJar;
        this.jarByType = jarByType;
    }

    /**
     * @return a Map from all jars in this graph to the types which they contain, indexed by name.
     */
    public Map<File, Map<String, TypeDefinition>> getTypesByJar() {
        return typesByJar;
    }

    /**
     * @return mapping from types to the jar where the type is located
     */
    public Map<String, File> getJarByType() {
        return jarByType;
    }

    /**
     * Find and return the definition and location for the given type.
     *
     * @param typeName name of the type
     * @return the type definition and location if it exists, null otherwise
     */
    public TypeDefinitionLocation findTypeDefinitionLocation(String typeName) {
        var jar = jarByType.get(typeName);
        if (jar != null) {
            var def = typesByJar.get(jar).get(typeName);
            if (def == null) return null;
            return new TypeDefinitionLocation(def, jar);
        }
        return null;
    }

    /**
     * Find and return the definition for the given type.
     *
     * @param typeName name of the type
     * @return the type definition if it exists, null otherwise
     */
    public TypeDefinition findTypeDefinition(String typeName) {
        var jar = jarByType.get(typeName);
        if (jar != null) {
            return typesByJar.get(jar).get(typeName);
        }
        return null;
    }

    public Set<Code> findImplementation(Code.Method method) {
        return findImplementation(method.typeName, method.toDefinition(), method.instruction.isVirtual());
    }

    public Set<Code> findImplementation(String typeName,
                                        Definition.MethodDefinition method,
                                        boolean isVirtualCall) {
        var typeDef = findTypeDefinition(typeName);
        if (typeDef == null) {
            var javaType = getJavaType(typeName);
            if (javaType != null && javaMethodExists(javaType, method, isVirtualCall)) {
                return Collections.emptySet();
            }
            return null;
        }

        var impl = typeDef.methods.get(method);
        if (impl != null) return impl;

        if (!isVirtualCall) return null;

        // try to find the method on the parent types
        for (var parentType : typeDef.type.getParentTypes()) {
            impl = findImplementation(parentType.name, method, true);
            if (impl != null) return impl;
        }
        return null;
    }

    /**
     * Check if a certain type exists.
     *
     * @param typeName the type name
     * @return true if the type exists, false otherwise
     */
    public boolean exists(String typeName) {
        return findTypeDefinition(typeName) != null || existsJava(typeName);
    }

    /**
     * Check if a certain field exists.
     *
     * @param field a field usage
     * @return true if the field exists, false otherwise
     */
    public boolean exists(Code.Field field) {
        return exists(field.typeName, field.toDefinition());
    }

    /**
     * Check if a certain definition exists.
     *
     * @param typeName   the type where the definition might exist
     * @param definition a definition being referenced from elsewhere
     * @return true if the definition exists in the type, false otherwise
     */
    public boolean exists(String typeName, Definition definition) {
        if (typeName.startsWith("\"[")) return existsJavaArray(definition);
        var typeDef = findTypeDefinition(typeName);
        if (typeDef == null) return existsJava(typeName, definition);
        var found = definition.match(
                typeDef.fields::contains,
                typeDef.methods::containsKey);
        if (found) return true;

        for (var parentType : typeDef.type.getParentTypes()) {
            if (exists(parentType.name, definition)) return true;
        }
        return false;
    }

    /**
     * Check if a certain type exists in the Java standard library,
     * or is a primitive type, or is an array type whose elements' type would return {@code true}
     * if passed to this method.
     *
     * @param typeName the type where the definition might exist
     * @return true if the Java type exists
     */
    public boolean existsJava(String typeName) {
        var type = cleanArrayTypeName(typeName);
        return getJavaType(type) != null || isPrimitiveJavaType(type);
    }

    /**
     * Check if a certain definition exists in the Java standard library.
     *
     * @param typeName   the type where the definition might exist
     * @param definition a definition being referenced from elsewhere
     * @return true if the definition exists in the standard library type, false otherwise
     */
    public boolean existsJava(String typeName, Definition definition) {
        if (typeName.startsWith("\"[")) return existsJavaArray(definition);
        var type = getJavaType(typeName);
        if (type == null) return false;
        return definition.match(f -> javaFieldExists(type, f), m -> javaMethodExists(type, m, false));
    }

    private boolean existsJavaArray(Definition definition) {
        return definition.match(ARRAY_LENGTH::equals, ARRAY_CLONE::equals);
    }

    private static Class<?> getJavaType(String typeName) {
        if (mayBeJavaStdLibType(typeName)) {
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
                // FIXME does this handle arrays?
                if (fieldType.equals(field.type)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean javaMethodExists(Class<?> type,
                                            Definition.MethodDefinition method,
                                            boolean isVirtualCall) {
        if (!isVirtualCall && method.name.equals("\"<init>\"")) {
            return javaConstructorExists(type, method.type);
        }

        for (var javaMethod : isVirtualCall ? type.getMethods() : type.getDeclaredMethods()) {
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

    /**
     * A {@link TypeDefinition} with its location jar.
     */
    public static final class TypeDefinitionLocation implements Describable {
        public final TypeDefinition typeDefinition;
        public final File jar;
        public final String className;

        public TypeDefinitionLocation(TypeDefinition typeDefinition, File jar) {
            this.typeDefinition = typeDefinition;
            this.jar = jar;
            this.className = typeNameToClassName(typeDefinition.typeName);
        }

        @Override
        public void describe(StringBuilder builder, boolean verbose) {
            builder.append(jar.getName()).append('!').append(className);
        }
    }

}
