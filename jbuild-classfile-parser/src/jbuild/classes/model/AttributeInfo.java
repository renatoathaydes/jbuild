package jbuild.classes.model;

import java.util.Arrays;

/**
 * attribute_info {
 * u2 attribute_name_index;
 * u4 attribute_length;
 * u1 info[attribute_length];
 * }
 */
public final class AttributeInfo {
    public final short nameIndex;
    public final byte[] attributes;

    public AttributeInfo(short nameIndex, byte[] attributes) {
        this.nameIndex = nameIndex;
        this.attributes = attributes;
    }

    @Override
    public String toString() {
        return "AttributeInfo{" +
                "nameIndex=" + nameIndex +
                ", attributes=" + Arrays.toString(attributes) +
                '}';
    }

    /**
     * Signature_attribute {
     * u2 attribute_name_index;
     * u4 attribute_length;
     * u2 signature_index;
     * }
     *
     * @param info attribute representing a Signature
     * @return the index of the signature in the constant pool.
     */
    public static short signatureAttributeValueIndex(AttributeInfo info) {
        assert info.attributes.length == 2;
        return (short) ((info.attributes[0] << 1) | (info.attributes[1]));
    }
}
