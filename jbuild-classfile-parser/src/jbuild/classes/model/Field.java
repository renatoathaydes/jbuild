package jbuild.classes.model;

import java.util.List;

/**
 * field_info {
 * u2             access_flags;
 * u2             name_index;
 * u2             descriptor_index;
 * u2             attributes_count;
 * attribute_info attributes[attributes_count];
 * }
 */
public final class Field {
    public final short accessFlags;
    public final short nameIndex;
    public final short descriptorIndex;
    public final List<Attribute> attributes;

    public Field(short accessFlags, short nameIndex, short descriptorIndex, List<Attribute> attributes) {
        this.accessFlags = accessFlags;
        this.nameIndex = nameIndex;
        this.descriptorIndex = descriptorIndex;
        this.attributes = attributes;
    }
}
