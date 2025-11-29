package jbuild.classes.model;

import jbuild.classes.model.attributes.AttributeInfo;

import java.util.List;

/**
 * method_info {
 * u2             access_flags;
 * u2             name_index;
 * u2             descriptor_index;
 * u2             attributes_count;
 * attribute_info attributes[attributes_count];
 * }
 */
public final class MethodInfo extends MemberInfo {

    public static final class AccessFlag {
        public static final int ACC_PUBLIC = 0x01;
        public static final int ACC_PRIVATE = 0x02;
        public static final int ACC_PROTECTED = 0x04;
        public static final int ACC_STATIC = 0x08;
        public static final int ACC_FINAL = 0x10;
        public static final int ACC_SYNCHRONIZED = 0x20;
        public static final int ACC_BRIDGE = 0x40;
        public static final int ACC_VARARGS = 0x80;
        public static final int ACC_NATIVE = 0x100;
        public static final int ACC_ABSTRACT = 0x400;
        public static final int ACC_STRICT = 0x800;
        public static short ACC_SYNTHETIC = 0x1000;

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

        public static boolean isSynchronized(int access) {
            return (access & ACC_SYNCHRONIZED) != 0;
        }

        public static boolean isBridge(int access) {
            return (access & ACC_BRIDGE) != 0;
        }

        public static boolean isVarArgs(int access) {
            return (access & ACC_VARARGS) != 0;
        }

        public static boolean isNative(int access) {
            return (access & ACC_NATIVE) != 0;
        }

        public static boolean isAbstract(int access) {
            return (access & ACC_ABSTRACT) != 0;
        }

        public static boolean isStrict(int access) {
            return (access & ACC_STRICT) != 0;
        }

        public static boolean isSynthetic(short accessFlags) {
            return (accessFlags & ACC_SYNTHETIC) != 0;
        }
    }

    public MethodInfo(short accessFlags, short nameIndex, short descriptorIndex, List<AttributeInfo> attributes) {
        super(accessFlags, nameIndex, descriptorIndex, attributes);
    }

    @Override
    public String toString() {
        return "MethodInfo{" +
                "accessFlags=" + accessFlags +
                ", nameIndex=" + nameIndex +
                ", descriptorIndex=" + descriptorIndex +
                ", attributes=" + attributes +
                '}';
    }
}
