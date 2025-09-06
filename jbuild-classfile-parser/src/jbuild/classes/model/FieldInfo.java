package jbuild.classes.model;

import jbuild.classes.model.attributes.AttributeInfo;

import java.util.List;

public final class FieldInfo {
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
