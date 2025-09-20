package jbuild.classes.model;

import jbuild.classes.model.attributes.AnnotationInfo;
import jbuild.classes.model.attributes.AttributeInfo;
import jbuild.classes.model.attributes.MethodParameter;
import jbuild.classes.model.attributes.ModuleAttribute;
import jbuild.classes.model.info.Reference;
import jbuild.classes.parser.AttributeParser;
import jbuild.classes.parser.JavaTypeSignatureParser;
import jbuild.classes.signature.MethodSignature;

import java.util.LinkedHashSet;
import java.util.List;
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

    private final AttributeParser attributeParser = new AttributeParser(this);

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
                .map(attr -> attributeParser.parseSourceFileAttribute(attr.attributes))
                .findFirst()
                .orElseThrow();
    }

    public Set<String> getTypesReferredTo() {
        if (typesReferredTo == null) {
            typesReferredTo = constPoolEntries.stream()
                    .filter(e -> e.tag == ConstPoolInfo.ConstClass.TAG)
                    .map(e -> nameOf((ConstPoolInfo.ConstClass) e))
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

    public List<List<AnnotationInfo>> getRuntimeVisibleParameterAnnotations(MethodInfo methodInfo) {
        return getMethodParameterAnnotationsAttribute("RuntimeVisibleParameterAnnotations", methodInfo.attributes);
    }

    public List<List<AnnotationInfo>> getRuntimeInvisibleParameterAnnotations(MethodInfo methodInfo) {
        return getMethodParameterAnnotationsAttribute("RuntimeInvisibleParameterAnnotations", methodInfo.attributes);
    }

    public MethodSignature getMethodTypeDescriptor(MethodInfo methodInfo) {
        return new JavaTypeSignatureParser().parseMethodSignature(
                getUtf8(methodInfo.descriptorIndex));
    }

    /**
     * Get the Signature attribute of the method if available.
     * <p>
     * The compiler only emits Signature attributes for generic types.
     * <p>
     * <quote>
     * A Java compiler must emit a signature for any class, interface, constructor, method, field,
     * or record component whose declaration uses type variables or parameterized types.
     * </quote>
     * <p>
     * Reference: <a href="https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.7.9.1">
     * Java spec section 4.7.9.1
     * </a>
     *
     * @param methodInfo the method (must be obtained from this class file)
     * @return the value of the Signature attribute or empty if unavailable.
     */
    public Optional<MethodSignature> getSignatureAttribute(MethodInfo methodInfo) {
        return methodInfo.attributes.stream()
                .filter(attr -> "Signature".equals(getUtf8(attr.nameIndex)))
                .findFirst()
                .map(attr -> new JavaTypeSignatureParser().parseMethodSignature(
                        getUtf8(attr.signatureAttributeValueIndex())));
    }

    /**
     * Get the MethodParameters attribute if available.
     *
     * @param methodInfo the method (must be obtained from this class file)
     * @return the value of the MethodParameters attribute or the empty List if unavailable.
     */
    public List<MethodParameter> getMethodParameters(MethodInfo methodInfo) {
        return methodInfo.attributes.stream()
                .filter(attr -> MethodParameter.ATTRIBUTE_NAME.equals(getUtf8(attr.nameIndex)))
                .findFirst()
                .map((attr) -> attributeParser.parseMethodParameters(attr.attributes))
                .orElse(List.of());
    }

    public Optional<ModuleAttribute> getModuleAttribute() {
        if (!AccessFlags.isModule(accessFlags)) {
            return Optional.empty();
        }
        var attribute = attributes.stream()
                .filter(attr -> ModuleAttribute.ATTRIBUTE_NAME.equals(getUtf8(attr.nameIndex)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Module attribute not found despite access flags " +
                        "indicating class file is a module"));

        return Optional.of(attributeParser.parseModuleAttribute(attribute));
    }

    public List<Reference> getReferences() {
        return constPoolEntries.stream()
                .filter(ConstPoolInfo.RefInfo.class::isInstance)
                .map(ConstPoolInfo.RefInfo.class::cast)
                .map(this::refOf)
                .collect(toList());
    }

    private List<AnnotationInfo> getAnnotationsAttribute(String name) {
        return attributes.stream()
                .filter(attr -> name.equals(getUtf8(attr.nameIndex)))
                .findFirst()
                .map(attribute -> attributeParser.parseAnnotationInfo(attribute.attributes))
                .orElse(List.of());
    }

    private List<List<AnnotationInfo>> getMethodParameterAnnotationsAttribute(
            String name, List<AttributeInfo> attributes) {
        return attributes.stream()
                .filter(attr -> name.equals(getUtf8(attr.nameIndex)))
                .findFirst()
                .map(attribute -> attributeParser.parseMethodParameter(attribute.attributes))
                .orElse(List.of());
    }

    private String nameOf(ConstPoolInfo.ConstClass type) {
        var utf8 = (ConstPoolInfo.Utf8) constPoolEntries.get(type.nameIndex & 0xFFFF);
        var name = utf8.asString();
        if (name.startsWith("[")) {
            // array types are already in the type name format
            return name;
        }
        return 'L' + name + ';';
    }

    private Reference refOf(ConstPoolInfo.RefInfo refInfo) {
        var constClass = (ConstPoolInfo.ConstClass) constPoolEntries.get(refInfo.classIndex & 0xFFFF);
        var nameAndType = (ConstPoolInfo.NameAndType) constPoolEntries.get(refInfo.nameAndTypeIndex & 0xFFFF);
        return new Reference(Reference.kindOf(refInfo), nameOf(constClass),
                getUtf8(nameAndType.nameIndex), getUtf8(nameAndType.descriptorIndex));
    }

    public String getUtf8(short index) {
        var utf8 = (ConstPoolInfo.Utf8) constPoolEntries.get(index & 0xFFFF);
        return utf8.asString();
    }

}
