package jbuild.java;

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

    public ClassGraph2.TypeDefinitionLocation findTypeDefinitionLocation(String typeName) {
    }
}
