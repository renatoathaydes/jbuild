package jbuild.classes;

import jbuild.classes.model.ClassFile;
import jbuild.classes.model.ConstPoolInfo;
import jbuild.classes.model.attributes.AnnotationInfo;
import jbuild.classes.model.attributes.ElementValuePair;
import jbuild.classes.model.attributes.EnumValue;

import java.util.ArrayList;
import java.util.List;

public final class AnnotationParser {
    private final ClassFile classFile;

    public AnnotationParser(ClassFile classFile) {
        this.classFile = classFile;
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
     * annotation {
     * u2 type_index;
     * u2 num_element_value_pairs;
     * {   u2            element_name_index;
     * element_value value;
     * } element_value_pairs[num_element_value_pairs];
     * }
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

    private String nextConstUf8(ByteScanner scanner) {
        var utf8 = (ConstPoolInfo.Utf8) classFile.constPoolEntries.get(scanner.nextShort());
        return utf8.asString();
    }

    private int nextConstInt(ByteScanner scanner) {
        var i = (ConstPoolInfo.ConstInt) classFile.constPoolEntries.get(scanner.nextShort());
        return i.value;
    }

    private double nextConstDouble(ByteScanner scanner) {
        var i = (ConstPoolInfo.ConstDouble) classFile.constPoolEntries.get(scanner.nextShort());
        return i.value;
    }

    private float nextConstFloat(ByteScanner scanner) {
        var i = (ConstPoolInfo.ConstFloat) classFile.constPoolEntries.get(scanner.nextShort());
        return i.value;
    }

    private long nextConstLong(ByteScanner scanner) {
        var i = (ConstPoolInfo.ConstLong) classFile.constPoolEntries.get(scanner.nextShort());
        return i.value;
    }

}
