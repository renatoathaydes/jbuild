package jbuild.classes.model.attributes;

import java.nio.ByteBuffer;
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
     * Calling this method assumes that this attribute is a Signature attribute:
     * <pre>
     * Signature_attribute {
     *   u2 attribute_name_index;
     *   u4 attribute_length;
     *   u2 signature_index;
     * }
     * </pre>
     *
     * @return the index of the Signature attribute in the constant pool.
     */
    public short signatureAttributeValueIndex() {
        assert attributes.length == 2;
        var buf = ByteBuffer.wrap(attributes);
        return buf.getShort();
    }
}
