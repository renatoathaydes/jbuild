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
}
