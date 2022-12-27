package jbuild.classes.model;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

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

    public final short minorVersion;
    public final MajorVersion majorVersion;
    public final List<ConstPoolInfo> constPoolEntries;
    public final short accessFlags;
    public final short thisClass;
    public final short superClass;
    public final short[] interfaces;
    public final List<FieldInfo> fields;
    public final List<MethodInfo> methods;
    public final List<AttributeInfo> attributes;

    // cached values
    private List<String> typesReferredTo;

    public ClassFile(short minorVersion,
                     short majorVersion,
                     List<ConstPoolInfo> constPoolEntries,
                     short accessFlags,
                     short thisClass,
                     short superClass,
                     short[] interfaces,
                     List<FieldInfo> fields,
                     List<MethodInfo> methods,
                     List<AttributeInfo> attributes) {
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

    public List<String> getTypesReferredTo() {
        if (typesReferredTo == null) {
            var thisClassInfo = (ConstPoolInfo.Class) constPoolEntries.get(thisClass);
            var thisClassNameIndex = thisClassInfo.nameIndex;
            typesReferredTo = constPoolEntries.stream()
                .filter(e -> e.tag == ConstPoolInfo.Class.TAG)
                .map(e -> (ConstPoolInfo.Class) e)
                .filter(c -> c.nameIndex != thisClassNameIndex)
                .map(this::nameOf)
                .filter(Objects::nonNull)
                .collect(toList());
        }
        return typesReferredTo;
    }

    private String nameOf(ConstPoolInfo.Class type) {
        var utf8 = (ConstPoolInfo.Utf8) constPoolEntries.get(type.nameIndex);
        if (isJavaClassName(utf8)) return null;
        return new String(utf8.value, StandardCharsets.UTF_8);
    }

    private static boolean isJavaClassName(ConstPoolInfo.Utf8 utf8) {
        var len = utf8.value.length;
        var minLen = JAVA_CLASSES_PREFIX.length;
        return len > minLen && Arrays.compare(utf8.value, 0, minLen, JAVA_CLASSES_PREFIX, 0, minLen) == 0;
    }

    private static final byte[] JAVA_CLASSES_PREFIX = "java/lang/".getBytes(StandardCharsets.UTF_8);
}
