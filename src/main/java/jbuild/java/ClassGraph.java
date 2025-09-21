package jbuild.java;

import jbuild.classes.model.ClassFile;
import jbuild.util.Describable;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static jbuild.util.JavaTypeUtils.cleanArrayTypeName;
import static jbuild.util.JavaTypeUtils.isPrimitiveJavaType;
import static jbuild.util.JavaTypeUtils.mayBeJavaStdLibType;
import static jbuild.util.JavaTypeUtils.parseTypeDescriptor;
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

    // cache of type references
    private final Map<String, Set<String>> typeRefsByType = new ConcurrentHashMap<>();

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

    public Set<String> getTypesReferredToBy(String typeName) {
        return typeRefsByType.computeIfAbsent(typeName, (ignore) -> {
            var typeDef = findTypeDefinition(typeName);
            if (typeDef == null) return null;
            return getTypesReferredToBy(typeDef.classFile);
        });
    }

    public static Set<String> getTypesReferredToBy(ClassFile classFile) {
        var result = new HashSet<String>();
        for (var ref : classFile.getReferences()) {
            result.add(ref.ownerType);
            result.addAll(parseTypeDescriptor(ref.descriptor, false));
        }
        for (var method : classFile.getMethods()) {
            result.addAll(parseTypeDescriptor(method.descriptor, false));
        }
        for (var field : classFile.getFields()) {
            result.addAll(parseTypeDescriptor(field.descriptor, false));
        }
        result.addAll(classFile.getConstClassNames());
        return result;
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
        /**
         * The name of this type (internal JVM name).
         */
        public final String typeName;
        /**
         * The name of this class or interface.
         */
        public final String className;
        /**
         * When used for keeping track of type references, which type referred to this one.
         * May be null.
         */
        public final TypeDefinitionLocation parent;

        public TypeDefinitionLocation(JavaType typeDefinition,
                                      File jar,
                                      TypeDefinitionLocation parent) {
            this.typeDefinition = typeDefinition;
            this.jar = jar;
            this.typeName = typeDefinition.classFile.getTypeName();
            this.className = typeDefinition.typeId.className;
            this.parent = parent;
        }

        public TypeDefinitionLocation(JavaType typeDefinition,
                                      File jar) {
            this(typeDefinition, jar, null);
        }

        public TypeDefinitionLocation withParent(TypeDefinitionLocation parent) {
            if (parent.equals(this)) {
                return this;
            }
            return new TypeDefinitionLocation(typeDefinition, jar, parent);
        }

        @Override
        public void describe(StringBuilder builder, boolean verbose) {
            builder.append(jar.getName()).append('!').append(className);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            TypeDefinitionLocation that = (TypeDefinitionLocation) o;
            return typeDefinition.equals(that.typeDefinition) && jar.equals(that.jar) && className.equals(that.className);
        }

        @Override
        public int hashCode() {
            int result = typeDefinition.hashCode();
            result = 31 * result + jar.hashCode();
            result = 31 * result + className.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return getDescription();
        }
    }

}
