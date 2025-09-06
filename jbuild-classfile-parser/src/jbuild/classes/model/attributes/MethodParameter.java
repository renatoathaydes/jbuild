package jbuild.classes.model.attributes;

public final class MethodParameter {
    public static final String ATTRIBUTE_NAME = "MethodParameters";

    public static final class AccessFlag {
        public static short NONE = 0;
        public static short ACC_FINAL = 0x0010;
        public static short ACC_SYNTHETIC = 0x1000;
        public static short ACC_MANDATED = 0x4000;

        public static boolean isNone(short accessFlags) {
            return accessFlags == 0;
        }

        public static boolean isFinal(short accessFlags) {
            return (accessFlags & ACC_FINAL) != 0;
        }

        public static boolean isSynthetic(short accessFlags) {
            return (accessFlags & ACC_SYNTHETIC) != 0;
        }

        public static boolean isMandated(short accessFlags) {
            return (accessFlags & ACC_MANDATED) != 0;
        }
    }

    public final short accessFlags;

    /**
     * The name of a method parameter.
     * <p>
     * It may be the empty String if the compiler did not include this attribute.
     */
    public final String name;

    public MethodParameter(short accessFlags, String name) {
        this.accessFlags = accessFlags;
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MethodParameter that = (MethodParameter) o;

        return accessFlags == that.accessFlags && name.equals(that.name);
    }

    public boolean isFinal() {
        return AccessFlag.isFinal(accessFlags);
    }

    public boolean isSynthetic() {
        return AccessFlag.isSynthetic(accessFlags);
    }

    public boolean isMandated() {
        return AccessFlag.isMandated(accessFlags);
    }

    @Override
    public int hashCode() {
        int result = Short.hashCode(accessFlags);
        result = 31 * result + name.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "MethodParameter{" +
                "accessFlags=" + accessFlags +
                ", name=" + name +
                '}';
    }
}
