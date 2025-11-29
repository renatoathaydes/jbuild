package jbuild.classes.model;

import java.nio.charset.StandardCharsets;

/**
 * cp_info {
 * u1 tag;
 * u1 info[];
 * }
 */
public abstract class ConstPoolInfo {
    public final short tag;

    private ConstPoolInfo(short tag) {
        this.tag = tag;
    }

    public static final class ConstClass extends ConstPoolInfo {
        public static final short TAG = 7;
        public final short nameIndex;

        public ConstClass(short nameIndex) {
            super(TAG);
            this.nameIndex = nameIndex;
        }
    }

    /**
     * Common representation of {@link FieldRef}, {@link MethodRef} and {@link InterfaceMethodRef}.
     */
    public static abstract class RefInfo extends ConstPoolInfo {
        public final short classIndex;
        public final short nameAndTypeIndex;

        protected RefInfo(short tag, short classIndex, short nameAndTypeIndex) {
            super(tag);
            this.classIndex = classIndex;
            this.nameAndTypeIndex = nameAndTypeIndex;
        }
    }

    public static final class FieldRef extends RefInfo {
        public static final short TAG = 9;

        public FieldRef(short classIndex, short nameAndTypeIndex) {
            super(TAG, classIndex, nameAndTypeIndex);
        }
    }

    public static final class MethodRef extends RefInfo {
        public static final short TAG = 10;

        public MethodRef(short classIndex, short nameAndTypeIndex) {
            super(TAG, classIndex, nameAndTypeIndex);
        }
    }

    public static final class InterfaceMethodRef extends RefInfo {
        public static final short TAG = 11;

        public InterfaceMethodRef(short classIndex, short nameAndTypeIndex) {
            super(TAG, classIndex, nameAndTypeIndex);
        }
    }

    public static final class ConstString extends ConstPoolInfo {
        public static final short TAG = 8;
        public final short stringIndex;

        public ConstString(short stringIndex) {
            super(TAG);
            this.stringIndex = stringIndex;
        }
    }

    public static final class ConstInt extends ConstPoolInfo {
        public static final short TAG = 3;
        public final int value;

        public ConstInt(int value) {
            super(TAG);
            this.value = value;
        }
    }

    public static final class ConstFloat extends ConstPoolInfo {
        public static final short TAG = 4;
        public final float value;

        public ConstFloat(float value) {
            super(TAG);
            this.value = value;
        }
    }

    public static final class ConstLong extends ConstPoolInfo {
        public static final short TAG = 5;
        public final long value;

        public ConstLong(long value) {
            super(TAG);
            this.value = value;
        }
    }

    public static final class ConstDouble extends ConstPoolInfo {
        public static final short TAG = 6;
        public final double value;

        public ConstDouble(double value) {
            super(TAG);
            this.value = value;
        }
    }

    /**
     * The {@link NameAndType} structure is used to represent a field or method, without indicating which class or
     * interface type it belongs to:
     * <p>
     * <pre>
     * CONSTANT_NameAndType_info {
     *   u1 tag;
     *   u2 name_index;
     *   u2 descriptor_index;
     * }
     * </pre>
     * <p>
     * This is used from:
     * <ul>
     *     <li>{@link RefInfo} subtypes (const pool)</li>
     *     <li>{@link DynamicInfo} (const pool)</li>
     *     <li>{@link InvokeDynamic} (const pool)</li>
     *     <li>{@link jbuild.classes.model.attributes.EnclosingMethod} (attribute)</li>
     * </ul>
     */
    public static final class NameAndType extends ConstPoolInfo {
        public static final short TAG = 12;
        public final short nameIndex;
        public final short descriptorIndex;

        public NameAndType(short nameIndex, short descriptorIndex) {
            super(TAG);
            this.nameIndex = nameIndex;
            this.descriptorIndex = descriptorIndex;
        }
    }

    public static final class Utf8 extends ConstPoolInfo {
        public static final short TAG = 1;
        public final byte[] value;

        public Utf8(byte[] value) {
            super(TAG);
            this.value = value;
        }

        public String asString() {
            return new String(value, StandardCharsets.UTF_8);
        }
    }

    public static final class MethodHandle extends ConstPoolInfo {
        public static final short TAG = 15;
        public final byte kind;
        public final short index;

        public MethodHandle(byte kind, short index) {
            super(TAG);
            this.kind = kind;
            this.index = index;
        }
    }

    public static final class MethodType extends ConstPoolInfo {
        public static final short TAG = 16;
        public final short descriptorIndex;

        public MethodType(short descriptorIndex) {
            super(TAG);
            this.descriptorIndex = descriptorIndex;
        }
    }

    public static final class DynamicInfo extends ConstPoolInfo {
        public static final short TAG = 17;
        public final short bootstrapMethodAttributeIndex;
        public final short nameAndTypeIndex;

        public DynamicInfo(short bootstrapMethodAttributeIndex, short descriptorIndex) {
            super(TAG);
            this.bootstrapMethodAttributeIndex = bootstrapMethodAttributeIndex;
            this.nameAndTypeIndex = descriptorIndex;
        }
    }

    public static final class InvokeDynamic extends ConstPoolInfo {
        public static final short TAG = 18;
        public final short bootstrapMethodAttributeIndex;
        public final short nameAndTypeIndex;

        public InvokeDynamic(short bootstrapMethodAttributeIndex, short descriptorIndex) {
            super(TAG);
            this.bootstrapMethodAttributeIndex = bootstrapMethodAttributeIndex;
            this.nameAndTypeIndex = descriptorIndex;
        }
    }

    public static final class ModuleInfo extends ConstPoolInfo {
        public static final short TAG = 19;
        public final short nameIndex;

        public ModuleInfo(short nameIndex) {
            super(TAG);
            this.nameIndex = nameIndex;
        }
    }

    public static final class PackageInfo extends ConstPoolInfo {
        public static final short TAG = 20;

        public final short nameIndex;

        public PackageInfo(short nameIndex) {
            super(TAG);
            this.nameIndex = nameIndex;
        }

    }
}
