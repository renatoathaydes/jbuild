package jbuild.classes.model.info;

import jbuild.classes.model.MemberInfo;

/**
 * A member of a type, such as a field or method.
 * <p>
 * A {@link MemberDefinition} is created from a {@link MemberInfo}.
 */
public final class MemberDefinition {

    /**
     * The unresolved member info this definition was created from.
     */
    public MemberInfo memberInfo;

    /**
     * The name of this member.
     */
    public final String name;

    /**
     * The type descriptor for this member.
     */
    public final String descriptor;

    public MemberDefinition(MemberInfo memberInfo, String name, String descriptor) {
        this.memberInfo = memberInfo;
        this.name = name;
        this.descriptor = descriptor;
    }
}
