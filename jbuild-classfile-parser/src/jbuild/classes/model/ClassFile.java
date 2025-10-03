package jbuild.classes.model;

import jbuild.classes.TypeGroup;
import jbuild.classes.model.attributes.AnnotationInfo;
import jbuild.classes.model.attributes.AttributeInfo;
import jbuild.classes.model.attributes.EnclosingMethod;
import jbuild.classes.model.attributes.MethodParameter;
import jbuild.classes.model.attributes.ModuleAttribute;
import jbuild.classes.model.attributes.SignatureAttribute;
import jbuild.classes.model.info.MemberDefinition;
import jbuild.classes.model.info.Reference;
import jbuild.classes.parser.AttributeParser;
import jbuild.classes.parser.JavaTypeSignatureParser;
import jbuild.classes.signature.ClassSignature;
import jbuild.classes.signature.JavaTypeSignature;
import jbuild.classes.signature.MethodSignature;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
public final class ClassFile implements TypeGroup {

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
    private final JavaTypeSignatureParser signatureParser = new JavaTypeSignatureParser();

    // cached values
    private volatile Set<String> typesReferredTo;
    private final String[] cachedUtf8;
    private final Map<ConstPoolInfo.ConstClass, String> cachedClassName = new ConcurrentHashMap<>();

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

        this.cachedUtf8 = new String[this.constPoolEntries.size()];
    }

    public String getTypeName() {
        var thisClassInfo = (ConstPoolInfo.ConstClass) constPoolEntries.get(thisClass & 0xFFFF);
        return nameOf(thisClassInfo);
    }

    public String getSuperClass() {
        var thisClassInfo = (ConstPoolInfo.ConstClass) constPoolEntries.get(superClass & 0xFFFF);
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

    @Override
    public Set<String> getAllTypes() {
        if (typesReferredTo == null) {
            synchronized (this) {
                if (typesReferredTo == null) {
                    var result = new LinkedHashSet<String>(64);
                    result.addAll(getConstClassNames());
                    getClassSignature().ifPresent(s -> result.addAll(s.getAllTypes()));

                    // Here, we first go through methods and fields, but also obtain their Signature attributes
                    // to get their generic descriptors.
                    getFields().stream()
                            .flatMap(f ->
                                    // if there's a signature attribute, it will contain the generic type
                                    getSignatureAttribute(f.memberInfo)
                                            .map(s -> s.getAllTypes().stream())
                                            .orElseGet(() ->
                                                    // otherwise, this will be non-generic type descriptor
                                                    signatureParser.parseJavaTypeSignature(f.descriptor).getAllTypes().stream())
                            )
                            .forEach(result::add);

                    getMethods().stream()
                            .flatMap(m ->
                                    // if there's a signature attribute, it will contain the generic type
                                    getSignatureAttribute(m.memberInfo)
                                            .map(s -> s.getAllTypes().stream())
                                            .orElseGet(() ->
                                                    // otherwise, this will be non-generic type descriptor
                                                    signatureParser.parseMethodSignature(m.descriptor).getAllTypes().stream())
                            )
                            .forEach(result::add);

                    getRuntimeVisibleAnnotations().stream()
                            .map(p -> p.typeName)
                            .forEach(result::add);
                    getReferences().stream()
                            .flatMap(ref -> signatureParser.parse(ref.descriptor, ref.kind).getAllTypes().stream())
                            .forEach(result::add);

                    // this is returning the type without the expected L and ;
                    getEnclosingMethodAttribute().ifPresent(m -> {
                        result.add(m.typeName);
                        if (m.method != null) {
                            result.addAll(signatureParser.parseMethodSignature(m.method.descriptor).getAllTypes());
                        }
                    });
                    typesReferredTo = result;
                }
            }
        }
        return typesReferredTo;
    }

    /**
     * @return the names of all {@link ConstPoolInfo.ConstClass} entries in the constant pool table.
     */
    public Set<String> getConstClassNames() {
        return constPoolEntries.stream()
                .filter(e -> e != null && e.tag == ConstPoolInfo.ConstClass.TAG)
                .map(e -> nameOf((ConstPoolInfo.ConstClass) e))
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * @return the {@link MemberDefinition}s representing fields of this class.
     */
    public List<MemberDefinition> getFields() {
        return fields.stream()
                .map(f -> new MemberDefinition(f, getUtf8(f.nameIndex), getUtf8(f.descriptorIndex)))
                .collect(Collectors.toList());
    }

    /**
     * @return the {@link MemberDefinition}s representing method of this class.
     */
    public List<MemberDefinition> getMethods() {
        return methods.stream()
                .map(m -> new MemberDefinition(m, getUtf8(m.nameIndex), getUtf8(m.descriptorIndex)))
                .collect(Collectors.toList());
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

    public JavaTypeSignature.ReferenceTypeSignature getFieldTypeDescriptor(FieldInfo fieldInfo) {
        return (JavaTypeSignature.ReferenceTypeSignature) signatureParser.parseJavaTypeSignature(
                getUtf8(fieldInfo.descriptorIndex));
    }

    public MethodSignature getMethodTypeDescriptor(MethodInfo methodInfo) {
        return signatureParser.parseMethodSignature(
                getUtf8(methodInfo.descriptorIndex));
    }

    public Optional<ClassSignature> getClassSignature() {
        return attributes.stream()
                .filter(attr -> SignatureAttribute.ATTRIBUTE_NAME.equals(getUtf8(attr.nameIndex)))
                .map(attr -> signatureParser.parseClassSignature(getUtf8(attr.signatureAttributeValueIndex())))
                .findFirst();
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
     * @param memberInfo the method (must be obtained from this class file)
     * @return the value of the Signature attribute or empty if unavailable.
     */
    public Optional<SignatureAttribute> getSignatureAttribute(MemberInfo memberInfo) {
        var isMethod = memberInfo instanceof MethodInfo;
        return memberInfo.attributes.stream()
                .filter(attr -> SignatureAttribute.ATTRIBUTE_NAME.equals(getUtf8(attr.nameIndex)))
                .findFirst()
                .map(attr -> signatureParser.parse(
                        getUtf8(attr.signatureAttributeValueIndex()),
                        isMethod ? Reference.RefKind.METHOD : Reference.RefKind.FIELD));
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

    /**
     * @return this class file's {@link EnclosingMethod} attribute, if present.
     */
    public Optional<EnclosingMethod> getEnclosingMethodAttribute() {
        return attributes.stream()
                .filter(attr -> EnclosingMethod.ATTRIBUTE_NAME.equals(getUtf8(attr.nameIndex)))
                .findFirst()
                .map(attribute -> attributeParser.parseEnclosingMethod(attribute.attributes));
    }

    /**
     * @return the references used by this class. Each {@link Reference} is created from a
     * {@link jbuild.classes.model.ConstPoolInfo.RefInfo} included in this class file.
     */
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
        return cachedClassName.computeIfAbsent(type, (k) -> {
            var name = getUtf8(type.nameIndex);
            var startIndex = name.lastIndexOf('[');
            if (startIndex != -1) {
                // array types are already in the type name format
                return name.substring(startIndex + 1);
            }
            return 'L' + name + ';';
        });
    }

    private Reference refOf(ConstPoolInfo.RefInfo refInfo) {
        var constClass = (ConstPoolInfo.ConstClass) constPoolEntries.get(refInfo.classIndex & 0xFFFF);
        var nameAndType = (ConstPoolInfo.NameAndType) constPoolEntries.get(refInfo.nameAndTypeIndex & 0xFFFF);
        return new Reference(Reference.kindOf(refInfo), nameOf(constClass),
                getUtf8(nameAndType.nameIndex), getUtf8(nameAndType.descriptorIndex));
    }

    private String getUtf8(short index) {
        var i = index & 0xFFFF;
        synchronized (cachedUtf8) {
            var cached = cachedUtf8[i];
            if (cached == null) {
                var utf8 = (ConstPoolInfo.Utf8) constPoolEntries.get(i);
                cached = cachedUtf8[i] = utf8.asString();
            }
            return cached;
        }
    }

}
