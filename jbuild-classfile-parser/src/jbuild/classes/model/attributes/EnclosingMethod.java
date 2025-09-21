package jbuild.classes.model.attributes;

/**
 * A class must have an EnclosingMethod attribute if and only if it represents a local class or an anonymous class
 * (JLS §14.3, JLS §15.9.5).
 * <p>
 * <pre>
 * EnclosingMethod_attribute {
 *     u2 attribute_name_index;
 *     u4 attribute_length;
 *     u2 class_index;
 *     u2 method_index;
 * }
 * </pre>
 */
public final class EnclosingMethod {
    public static final String ATTRIBUTE_NAME = "EnclosingMethod";

    public static final class MethodDescriptor {
        public final String methodName;
        public final String descriptor;

        public MethodDescriptor(String methodName, String descriptor) {
            this.methodName = methodName;
            this.descriptor = descriptor;
        }
    }

    /**
     * The class enclosing the class or interface this attribute is found on.
     */
    public final String typeName;

    /**
     * The method, if the current class is immediately enclosed by a method or constructor,
     * otherwise {@code null}.
     */
    public final MethodDescriptor method;

    public EnclosingMethod(String typeName, MethodDescriptor method) {
        this.typeName = typeName;
        this.method = method;
    }

    public EnclosingMethod(String typeName) {
        this(typeName, null);
    }

}
