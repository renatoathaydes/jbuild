package jbuild.classes.model;

import java.util.List;

/**
 * ClassFile {
 * u4             magic;
 * u2             minor_version;
 * u2             major_version;
 * u2             constant_pool_count;
 * cp_info        constant_pool[constant_pool_count-1];
 * u2             access_flags;
 * u2             this_class;
 * u2             super_class;
 * u2             interfaces_count;
 * u2             interfaces[interfaces_count];
 * u2             fields_count;
 * field_info     fields[fields_count];
 * u2             methods_count;
 * method_info    methods[methods_count];
 * u2             attributes_count;
 * attribute_info attributes[attributes_count];
 * }
 */
public final class ClassFile {

    public static final int MAGIC = 0xCAFEBABE;

    public final int magic;
    public final short minorVersion;
    public final MajorVersion majorVersion;
    public final List<ConstPoolInfo> constPoolEntries;
    public final short accessFlags;
    public final short thisClass;
    public final short superClass;
    public final short[] interfaces;
    public final List<Field> fields;
    public final List<Method> methods;
    public final List<Attribute> attributes;

    public ClassFile(int magic,
                     short minorVersion,
                     short majorVersion,
                     List<ConstPoolInfo> constPoolEntries,
                     short accessFlags,
                     short thisClass,
                     short superClass,
                     short[] interfaces,
                     List<Field> fields,
                     List<Method> methods,
                     List<Attribute> attributes) {
        this.magic = magic;
        this.minorVersion = minorVersion;
        this.majorVersion = new MajorVersion(majorVersion);
        this.constPoolEntries = constPoolEntries;
        this.accessFlags = accessFlags;
        this.thisClass = thisClass;
        this.superClass = superClass;
        this.interfaces = interfaces;
        this.fields = fields;
        this.methods = methods;
        this.attributes = attributes;
    }
}
