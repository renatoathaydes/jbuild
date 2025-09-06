package jbuild.classes.model;

/**
 * The value of the access_flags item is a mask of flags used to denote access permissions
 * to and properties of this class or interface.
 * <p>
 * The interpretation of each flag, when set, is specified in Table 4.1-B.
 * <p>
 * The methods of this classs should be passed as argument the value of
 * {@link jbuild.classes.model.ClassFile#accessFlags}.
 */
public final class AccessFlags {

    public static final short ACC_PUBLIC = 0x01;
    public static final short ACC_FINAL = 0x0010;
    public static final short ACC_SUPER = 0x0020;
    public static final short ACC_INTERFACE = 0x0200;
    public static final short ACC_ABSTRACT = 0x0400;
    public static final short ACC_SYNTHETIC = 0x1000;
    public static final short ACC_ANNOTATION = 0x2000;
    public static final short ACC_ENUM = 0x4000;
    public static final short ACC_MODULE = (short) 0x8000;

    private AccessFlags() {
    }

    public static boolean isPublic(short accessFlags) {
        return (accessFlags & ACC_PUBLIC) != 0;
    }

    public static boolean isFinal(short accessFlags) {
        return (accessFlags & ACC_FINAL) != 0;
    }

    public static boolean isSuper(short accessFlags) {
        return (accessFlags & ACC_SUPER) != 0;
    }

    public static boolean isInterface(short accessFlags) {
        return (accessFlags & ACC_INTERFACE) != 0;
    }

    public static boolean isAbstract(short accessFlags) {
        return (accessFlags & ACC_ABSTRACT) != 0;
    }

    public static boolean isSynthetic(short accessFlags) {
        return (accessFlags & ACC_SYNTHETIC) != 0;
    }

    public static boolean isAnnotation(short accessFlags) {
        return (accessFlags & ACC_ANNOTATION) != 0;
    }

    public static boolean isEnum(short accessFlags) {
        return (accessFlags & ACC_ENUM) != 0;
    }

    public static boolean isModule(short accessFlags) {
        return (accessFlags & ACC_MODULE) != 0;
    }
}
