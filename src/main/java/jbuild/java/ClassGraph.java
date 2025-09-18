package jbuild.java;

import jbuild.util.Describable;

import java.io.File;
import java.util.Map;

import static jbuild.util.JavaTypeUtils.cleanArrayTypeName;
import static jbuild.util.JavaTypeUtils.isPrimitiveJavaType;
import static jbuild.util.JavaTypeUtils.mayBeJavaStdLibType;
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

    private final Map<File, Map<String, JavaType>> typesByJar;
    private final Map<String, File> jarByType;

    public ClassGraph(Map<File, Map<String, JavaType>> typesByJar,
                      Map<String, File> jarByType) {
        this.typesByJar = typesByJar;
        this.jarByType = jarByType;
    }

    /**
     * @return a Map from all jars in this graph to the types which they contain, indexed by name.
     */
    public Map<File, Map<String, JavaType>> getTypesByJar() {
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
    public JavaType findTypeDefinition(String typeName) {
        var jar = jarByType.get(typeName);
        if (jar != null) {
            return typesByJar.get(jar).get(typeName);
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

    /**
     * A {@link JavaType} with its location jar.
     */
    public static final class TypeDefinitionLocation implements Describable {
        public final JavaType typeDefinition;
        public final File jar;
        public final String className;

        public TypeDefinitionLocation(JavaType typeDefinition, File jar) {
            this.typeDefinition = typeDefinition;
            this.jar = jar;
            this.className = typeNameToClassName(typeDefinition.typeId.name);
        }

        @Override
        public void describe(StringBuilder builder, boolean verbose) {
            builder.append(jar.getName()).append('!').append(className);
        }

        @Override
        public String toString() {
            return getDescription();
        }
    }

}
