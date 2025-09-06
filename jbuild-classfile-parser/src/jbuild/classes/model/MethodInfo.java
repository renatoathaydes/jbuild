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
public final class MethodInfo {
    public final short accessFlags;
    public final short nameIndex;
    public final short descriptorIndex;
    public final List<AttributeInfo> attributes;

    public MethodInfo(short accessFlags, short nameIndex, short descriptorIndex, List<AttributeInfo> attributes) {
        this.accessFlags = accessFlags;
        this.nameIndex = nameIndex;
        this.descriptorIndex = descriptorIndex;
        this.attributes = attributes;
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
