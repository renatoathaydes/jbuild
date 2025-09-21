package jbuild.classes.parser;

import jbuild.classes.model.ClassFile;
import jbuild.classes.model.attributes.AnnotationInfo;
import jbuild.classes.model.attributes.AttributeInfo;
import jbuild.classes.model.attributes.ElementValuePair;
import jbuild.classes.model.attributes.EnclosingMethod;
import jbuild.classes.model.attributes.EnumValue;
import jbuild.classes.model.attributes.MethodParameter;
import jbuild.classes.model.attributes.ModuleAttribute;

import java.util.ArrayList;
import java.util.List;

public final class AttributeParser extends AbstractAttributeParser {

    private final ModuleAttributeParser moduleAttributeParser;
    private final MethodParametersParser methodParametersParser;

    public AttributeParser(ClassFile classFile) {
        super(classFile);
        moduleAttributeParser = new ModuleAttributeParser(classFile);
        methodParametersParser = new MethodParametersParser(classFile);
    }

    public List<AnnotationInfo> parseAnnotationInfo(byte[] attributes) {
        var scanner = new ByteScanner(attributes);
        var length = scanner.nextShort();
        var result = new ArrayList<AnnotationInfo>(length);
        for (var i = 0; i < length; i++) {
            result.add(parse(scanner));
        }
        return result;
    }

    /**
     * <pre>
     * {@code
     * RuntimeVisibleParameterAnnotations_attribute {
     *     u2 attribute_name_index;
     *     u4 attribute_length;
     *     u1 num_parameters;
     *     {   u2         num_annotations;
     *         annotation annotations[num_annotations];
     *     } parameter_annotations[num_parameters];
     * }
     * }
     * </pre>
     *
     * @param attributes attributes structure
     * @return method parameter annotations
     */
    public List<List<AnnotationInfo>> parseMethodParameter(byte[] attributes) {
        var scanner = new ByteScanner(attributes);
        var parameterCount = scanner.nextByte();
        var result = new ArrayList<List<AnnotationInfo>>(parameterCount);
        for (var i = 0; i < parameterCount; i++) {
            var length = scanner.nextShort();
            var annotations = new ArrayList<AnnotationInfo>(length);
            for (var j = 0; j < length; j++) {
                annotations.add(parse(scanner));
            }
            result.add(annotations);
        }
        return result;
    }

    /**
     * <pre>
     * {@code
     * annotation {
     * u2 type_index;
     * u2 num_element_value_pairs;
     * {   u2            element_name_index;
     * element_value value;
     * } element_value_pairs[num_element_value_pairs];
     * }
     * }
     * </pre>
     */
    private AnnotationInfo parse(ByteScanner scanner) {
        var typeDescriptor = nextConstUf8(scanner);
        var length = scanner.nextShort();
        var values = new ArrayList<ElementValuePair>(length);
        for (var i = 0; i < length; i++) {
            var name = nextConstUf8(scanner);
            values.add(parseValuePair(scanner, name));
        }
        return new AnnotationInfo(typeDescriptor, values);
    }

    /**
     * element_value {
     * u1 tag;
     * union {
     * u2 const_value_index;
     * <p>
     * {   u2 type_name_index;
     * u2 const_name_index;
     * } enum_const_value;
     * <p>
     * u2 class_info_index;
     * <p>
     * annotation annotation_value;
     * <p>
     * {   u2            num_values;
     * element_value values[num_values];
     * } array_value;
     * } value;
     * }
     */
    private ElementValuePair parseValuePair(ByteScanner scanner, String name) {
        var type = ElementValuePair.Type.from((char) scanner.nextByte());
        return new ElementValuePair(name, type, parseValue(scanner, type));
    }

    private Object parseValue(ByteScanner scanner, ElementValuePair.Type type) {
        switch (type) {
            case BYTE:
                return (byte) nextConstInt(scanner);
            case CHAR:
                return (char) nextConstInt(scanner);
            case INT:
                return nextConstInt(scanner);
            case SHORT:
                return (short) nextConstInt(scanner);
            case BOOL:
                return nextConstInt(scanner) != 0;
            case DOUBLE:
                return nextConstDouble(scanner);
            case FLOAT:
                return nextConstFloat(scanner);
            case LONG:
                return nextConstLong(scanner);
            case STRING:
            case CLASS:
                return nextConstUf8(scanner);
            case ENUM: {
                var typeName = nextConstUf8(scanner);
                var constName = nextConstUf8(scanner);
                return new EnumValue(typeName, constName);
            }
            case ANNOTATION:
                return parse(scanner);
            case ARRAY: {
                var length = scanner.nextShort();
                var array = new ArrayList<>(length);
                for (var i = 0; i < length; i++) {
                    var elemType = ElementValuePair.Type.from((char) scanner.nextByte());
                    array.add(parseValue(scanner, elemType));
                }
                return array;
            }
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    public String parseSourceFileAttribute(byte[] bytes) {
        return nextConstUf8(new ByteScanner(bytes));
    }

    public EnclosingMethod parseEnclosingMethod(byte[] bytes) {
        var scanner = new ByteScanner(bytes);
        var className = nextConstClass(scanner);
        return nextConstNameAndType(scanner).map(nt ->
                        new EnclosingMethod(className, new EnclosingMethod.MethodDescriptor(
                                constUtf8(nt.nameIndex),
                                constUtf8(nt.descriptorIndex))))
                .orElseGet(() -> new EnclosingMethod(className));
    }

    public ModuleAttribute parseModuleAttribute(AttributeInfo attribute) {
        return moduleAttributeParser.parseModuleAttribute(attribute);
    }

    public List<MethodParameter> parseMethodParameters(byte[] attributes) {
        return methodParametersParser.parseMethodParameters(attributes);
    }
}
