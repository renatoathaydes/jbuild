package jbuild.classes.model.info;

import jbuild.classes.model.ConstPoolInfo;

/**
 * Representation of a <a href="https://docs.oracle.com/javase/specs/jvms/se24/html/jvms-4.html#jvms-4.4.2">
 * CONSTANT_Fieldref_info, CONSTANT_Methodref_info, CONSTANT_InterfaceMethodref_info</a>.
 * <p>
 * This is the resolved form of a {@link jbuild.classes.model.ConstPoolInfo.RefInfo}.
 */
public final class Reference {

    public static RefKind kindOf(ConstPoolInfo.RefInfo refInfo) {
        assert (refInfo.tag == ConstPoolInfo.FieldRef.TAG ||
                refInfo.tag == ConstPoolInfo.MethodRef.TAG ||
                refInfo.tag == ConstPoolInfo.InterfaceMethodRef.TAG);
        return refInfo.tag == ConstPoolInfo.FieldRef.TAG
                ? RefKind.FIELD
                : refInfo.tag == ConstPoolInfo.MethodRef.TAG
                ? RefKind.METHOD
                : RefKind.INTERFACE_METHOD;
    }

    public enum RefKind {
        FIELD, METHOD, INTERFACE_METHOD,
    }

    public final RefKind kind;
    /**
     * A class or interface type that has the field or method as a member.
     */
    public final String ownerType;
    public final String name;
    /**
     * a valid <a href="https://docs.oracle.com/javase/specs/jvms/se24/html/jvms-4.html#jvms-4.3.2">field descriptor</a>
     * or <a href="https://docs.oracle.com/javase/specs/jvms/se24/html/jvms-4.html#jvms-4.3.3">method descriptor</a>.
     */
    public final String descriptor;

    public Reference(RefKind kind, String ownerType, String name, String descriptor) {
        this.kind = kind;
        this.ownerType = ownerType;
        this.name = name;
        this.descriptor = descriptor;
    }
}
