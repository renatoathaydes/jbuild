package jbuild.classes.model;

import jbuild.classes.model.attributes.AttributeInfo;

import java.util.List;

public final class FieldInfo {

    public static final class AccessFlag {
        public static final int ACC_PUBLIC = 0x01;
        public static final int ACC_PRIVATE = 0x02;
        public static final int ACC_PROTECTED = 0x04;
        public static final int ACC_STATIC = 0x08;
        public static final int ACC_FINAL = 0x10;
        public static final int ACC_VOLATILE = 0x40;
        public static final int ACC_TRANSIENT = 0x80;
        public static short ACC_SYNTHETIC = 0x1000;
        public static short ACC_ENUM = 0x4000;

        public static boolean isPublic(int access) {
            return (access & ACC_PUBLIC) != 0;
        }

        public static boolean isPrivate(int access) {
            return (access & ACC_PRIVATE) != 0;
        }

        public static boolean isProtected(int access) {
            return (access & ACC_PROTECTED) != 0;
        }

        public static boolean isStatic(int access) {
            return (access & ACC_STATIC) != 0;
        }

        public static boolean isFinal(short accessFlags) {
            return (accessFlags & ACC_FINAL) != 0;
        }

        public static boolean isVolatile(short accessFlags) {
            return (accessFlags & ACC_VOLATILE) != 0;
        }

        public static boolean isTransient(short accessFlags) {
            return (accessFlags & ACC_TRANSIENT) != 0;
        }

        public static boolean isSynthetic(short accessFlags) {
            return (accessFlags & ACC_SYNTHETIC) != 0;
        }

        public static boolean isEnum(short accessFlags) {
            return (accessFlags & ACC_ENUM) != 0;
        }
    }

    public final short accessFlags;
    public final short nameIndex;
    public final short descriptorIndex;
    public final List<AttributeInfo> attributes;

    public FieldInfo(short accessFlags, short nameIndex, short descriptorIndex, List<AttributeInfo> attributes) {
        this.accessFlags = accessFlags;
        this.nameIndex = nameIndex;
        this.descriptorIndex = descriptorIndex;
        this.attributes = attributes;
    }
}
