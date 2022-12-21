package jbuild.classes;

/**
 * attribute_info {
 * u2 attribute_name_index;
 * u4 attribute_length;
 * u1 info[attribute_length];
 * }
 */
public final class Attribute {
    public final short nameIndex;
    public final byte[] attributes;

    public Attribute(short nameIndex, byte[] attributes) {
        this.nameIndex = nameIndex;
        this.attributes = attributes;
    }
}
