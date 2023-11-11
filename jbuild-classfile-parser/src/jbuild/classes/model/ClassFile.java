package jbuild.classes.model;

import jbuild.classes.AnnotationParser;
import jbuild.classes.ByteScanner;
import jbuild.classes.model.attributes.AnnotationInfo;
import jbuild.classes.model.attributes.MethodParameter;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

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
    private Set<String> typesReferredTo;

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

    public String getTypeName() {
        var thisClassInfo = (ConstPoolInfo.ConstClass) constPoolEntries.get(thisClass & 0xFFFF);
        return nameOf(thisClassInfo);
    }

    public List<MethodInfo> getConstructors() {
        return methods.stream().filter((m) -> getUtf8(m.nameIndex).equals("<init>"))
                .collect(toList());
    }

    public Set<String> getInterfaceNames() {
        var result = new LinkedHashSet<String>(interfaces.length);
        for (short interfaceIndex : interfaces) {
            var interf = (ConstPoolInfo.ConstClass) constPoolEntries.get(interfaceIndex & 0xFFFF);
            result.add(nameOf(interf));
        }
        return result;
    }

    public String getSourceFile() {
        return attributes.stream()
                .filter(attr -> getUtf8(attr.nameIndex).equals("SourceFile"))
                .map(attr -> getUtf8(new ByteScanner(attr.attributes).nextShort()))
                .findFirst()
                .orElseThrow();
    }

    public Set<String> getTypesReferredTo() {
        if (typesReferredTo == null) {
            var thisClassInfo = (ConstPoolInfo.ConstClass) constPoolEntries.get(thisClass & 0xFFFF);
            var thisClassNameIndex = thisClassInfo.nameIndex;
            typesReferredTo = constPoolEntries.stream()
                    .filter(e -> e != null &&
                            (e.tag == ConstPoolInfo.ConstClass.TAG || e.tag == ConstPoolInfo.NameAndType.TAG))
                    .map(e -> typeName(e, thisClassNameIndex))
                    .filter(Objects::nonNull)
                    .collect(toSet());
        }
        return typesReferredTo;
    }

    public List<AnnotationInfo> getRuntimeVisibleAnnotations() {
        return getAnnotationsAttribute("RuntimeVisibleAnnotations");
    }

    public List<AnnotationInfo> getRuntimeInvisibleAnnotations() {
        return getAnnotationsAttribute("RuntimeInvisibleAnnotations");
    }

    public String getTypeDescriptor(MethodInfo methodInfo) {
        return getUtf8(methodInfo.descriptorIndex);
    }

    /**
     * Get the Signature attribute of the method if available.
     *
     * @param methodInfo the method (must be obtained from this class file)
     * @return the value of the Signature attribute or empty if unavailable.
     */
    public Optional<String> getSignatureAttribute(MethodInfo methodInfo) {
        return methodInfo.attributes.stream()
                .filter(attr -> "Signature".equals(getUtf8(attr.nameIndex)))
                .findFirst()
                .map(attr -> getUtf8(attr.signatureAttributeValueIndex()));
    }

    /**
     * Get the MethodParameters attribute if available.
     *
     * @param methodInfo the method (must be obtained from this class file)
     * @return the value of the MethodParameters attribute or the empty List if unavailable.
     */
    public List<MethodParameter> getMethodParameters(MethodInfo methodInfo) {
        var methodParamsParser = new MethodParametersParser(this);
        return methodInfo.attributes.stream()
                .filter(attr -> "MethodParameters".equals(getUtf8(attr.nameIndex)))
                .findFirst()
                .map((attr) -> methodParamsParser.parseMethodParameters(attr.attributes))
                .orElse(List.of());
    }

    private List<AnnotationInfo> getAnnotationsAttribute(String name) {
        var annotationParser = new AnnotationParser(this);
        return attributes.stream()
                .filter(attr -> name.equals(getUtf8(attr.nameIndex)))
                .findFirst()
                .map(attribute -> annotationParser.parseAnnotationInfo(attribute.attributes))
                .orElse(List.of());
    }

    private String nameOf(ConstPoolInfo.ConstClass type) {
        var utf8 = (ConstPoolInfo.Utf8) constPoolEntries.get(type.nameIndex & 0xFFFF);
        if (isJavaClassName(utf8)) return null;
        var name = utf8.asString();
        if (name.startsWith("[")) {
            // array types are already in the type name format
            return name;
        }
        return 'L' + name + ';';
    }

    private String getUtf8(short index) {
        var utf8 = (ConstPoolInfo.Utf8) constPoolEntries.get(index & 0xFFFF);
        return utf8.asString();
    }

    private String typeName(ConstPoolInfo info, short nameIndex) {
        if (info.tag == ConstPoolInfo.ConstClass.TAG) {
            var classInfo = (ConstPoolInfo.ConstClass) info;
            if (classInfo.nameIndex == nameIndex)
                return null;
            return nameOf(classInfo);
        }
        if (info.tag == ConstPoolInfo.NameAndType.TAG) {
            var nameAndType = (ConstPoolInfo.NameAndType) info;
            if (nameAndType.descriptorIndex == nameIndex)
                return null;
            return getUtf8(nameAndType.descriptorIndex);
        }
        throw new IllegalArgumentException("unsupported type: " + info.getClass().getSimpleName());
    }

    private static boolean isJavaClassName(ConstPoolInfo.Utf8 utf8) {
        var len = utf8.value.length;
        var minLen = JAVA_CLASSES_PREFIX.length;
        return len > minLen && Arrays.compare(utf8.value, 0, minLen, JAVA_CLASSES_PREFIX, 0, minLen) == 0;
    }

    private static final byte[] JAVA_CLASSES_PREFIX = "java/lang/".getBytes(StandardCharsets.UTF_8);
}
