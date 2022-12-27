package jbuild.classes;

import jbuild.classes.model.AttributeInfo;
import jbuild.classes.model.ClassFile;
import jbuild.classes.model.ConstPoolInfo;
import jbuild.classes.model.FieldInfo;
import jbuild.classes.model.MethodInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class JBuildClassFileParser {

    /**
     * Always the first item in the constant pool.
     */
    public static final ConstPoolInfo.Utf8 FIRST_ITEM_SENTINEL = new ConstPoolInfo.Utf8(new byte[0]);

    ClassFile parse(InputStream input) throws IOException {
        var scanner = new PositionScanner(input);
        var magic = scanner.nextInt();

        // ClassFile {
        //    u4             magic;
        //    u2             minor_version;
        //    u2             major_version;
        //    u2             constant_pool_count;
        //    cp_info        constant_pool[constant_pool_count-1];
        //    u2             access_flags;
        //    u2             this_class;
        //    u2             super_class;
        //    u2             interfaces_count;
        //    u2             interfaces[interfaces_count];
        //    u2             fields_count;
        //    field_info     fields[fields_count];
        //    u2             methods_count;
        //    method_info    methods[methods_count];
        //    u2             attributes_count;
        //    attribute_info attributes[attributes_count];
        //}

        if (magic != ClassFile.MAGIC) {
            throw new ClassFileException("Not a Java class file", 0);
        }

        var minor = scanner.nextShort();
        var major = scanner.nextShort();
        var constPool = parseConstPool(scanner, scanner.nextShort());
        var accessFlags = scanner.nextShort();
        var thisClass = scanner.nextShort();
        var superClass = scanner.nextShort();
        var interfaces = parseInterfaces(scanner, scanner.nextShort());
        var fields = parseFields(scanner, scanner.nextShort());
        var methods = parseMethods(scanner, scanner.nextShort());
        var attributes = parseAttributes(scanner, scanner.nextShort());

        return new ClassFile(minor, major, constPool, accessFlags, thisClass, superClass, interfaces,
                fields, methods, attributes);
    }

    private List<ConstPoolInfo> parseConstPool(PositionScanner scanner, short constPoolCount) {
        // The value of the constant_pool_count item is equal to the number of entries in the constant_pool table plus one
        int constPoolSize = 0xff & constPoolCount;
        var result = new ArrayList<ConstPoolInfo>(constPoolSize);
        // first item is always a dummy value, so the rest of the items fall into the appropriate index
        result.add(FIRST_ITEM_SENTINEL);
        for (var i = 1; i < constPoolSize; i++) {
            result.add(parseConstPoolInfo(scanner));
        }
        return result;
    }

    private ConstPoolInfo parseConstPoolInfo(PositionScanner scanner) {
        var tag = scanner.nextByte();
        switch (tag) {
            case ConstPoolInfo.Class.TAG:
                return new ConstPoolInfo.Class(scanner.nextShort());
            case ConstPoolInfo.FieldRef.TAG:
                return new ConstPoolInfo.FieldRef(scanner.nextShort(), scanner.nextShort());
            case ConstPoolInfo.MethodRef.TAG:
                return new ConstPoolInfo.MethodRef(scanner.nextShort(), scanner.nextShort());
            case ConstPoolInfo.InterfaceMethodRef.TAG:
                return new ConstPoolInfo.InterfaceMethodRef(scanner.nextShort(), scanner.nextShort());
            case ConstPoolInfo.String.TAG:
                return new ConstPoolInfo.String(scanner.nextShort());
            case ConstPoolInfo.Int.TAG:
                return new ConstPoolInfo.Int(scanner.nextShort());
            case ConstPoolInfo.Float.TAG:
                return new ConstPoolInfo.Float(scanner.nextShort());
            case ConstPoolInfo.Long.TAG:
                return new ConstPoolInfo.Long(scanner.nextShort());
            case ConstPoolInfo.Double.TAG:
                return new ConstPoolInfo.Double(scanner.nextShort());
            case ConstPoolInfo.NameAndType.TAG:
                return new ConstPoolInfo.NameAndType(scanner.nextShort(), scanner.nextShort());
            case ConstPoolInfo.Utf8.TAG: {
                var length = scanner.nextShort();
                var contents = new byte[length];
                scanner.next(contents);
                return new ConstPoolInfo.Utf8(contents);
            }
            case ConstPoolInfo.MethodHandle.TAG:
                return new ConstPoolInfo.MethodHandle(scanner.nextByte(), scanner.nextShort());
            case ConstPoolInfo.MethodType.TAG:
                return new ConstPoolInfo.MethodType(scanner.nextShort());
            case ConstPoolInfo.InvokeDynamic.TAG:
                return new ConstPoolInfo.InvokeDynamic(scanner.nextShort(), scanner.nextShort());
            default:
                throw new ClassFileException("Unknown constant pool tag: " + tag, 0);
        }
    }

    private short[] parseInterfaces(PositionScanner scanner, short interfaceCount) {
        var result = new short[interfaceCount];
        for (var i = 0; i < interfaceCount; i++) {
            result[i] = scanner.nextShort();
        }
        return result;
    }

    private List<FieldInfo> parseFields(PositionScanner scanner, short interfaceCount) {
        var result = new ArrayList<FieldInfo>(interfaceCount);
        for (var i = 0; i < interfaceCount; i++) {
            result.add(parseField(scanner));
        }
        return result;
    }

    /**
     * field_info {
     * u2             access_flags;
     * u2             name_index;
     * u2             descriptor_index;
     * u2             attributes_count;
     * attribute_info attributes[attributes_count];
     * }
     */
    private FieldInfo parseField(PositionScanner scanner) {
        return new FieldInfo(scanner.nextShort(),
                scanner.nextShort(),
                scanner.nextShort(),
                parseAttributes(scanner, scanner.nextShort()));
    }

    private List<MethodInfo> parseMethods(PositionScanner scanner, short length) {
        var result = new ArrayList<MethodInfo>(length);
        for (var i = 0; i < length; i++) {
            result.add(parseMethod(scanner));
        }
        return result;
    }

    /**
     * method_info {
     * u2             access_flags;
     * u2             name_index;
     * u2             descriptor_index;
     * u2             attributes_count;
     * attribute_info attributes[attributes_count];
     * }
     */
    private MethodInfo parseMethod(PositionScanner scanner) {
        return new MethodInfo(scanner.nextShort(),
                scanner.nextShort(),
                scanner.nextShort(),
                parseAttributes(scanner, scanner.nextShort()));
    }

    /**
     * attribute_info {
     * u2 attribute_name_index;
     * u4 attribute_length;
     * u1 info[attribute_length];
     * }
     */
    private List<AttributeInfo> parseAttributes(PositionScanner scanner, short length) {
        var attributes = new ArrayList<AttributeInfo>(length);
        for (var i = 0; i < length; i++) {
            var nameIndex = scanner.nextShort();
            var valueLength = scanner.nextInt();
            var value = new byte[valueLength];
            scanner.next(value);
            attributes.add(new AttributeInfo(nameIndex, value));
        }
        return attributes;
    }
}

final class PositionScanner {
    private final ByteBuffer buffer;

    public PositionScanner(InputStream stream) throws IOException {
        buffer = ByteBuffer.wrap(stream.readAllBytes());
        buffer.position(0);
        buffer.order(ByteOrder.BIG_ENDIAN);
    }

    int position() {
        return buffer.position();
    }

    int nextInt() {
        return buffer.getInt();
    }

    short nextShort() {
        return buffer.getShort();
    }

    byte nextByte() {
        return buffer.get();
    }

    public void next(byte[] contents) {
        buffer.get(contents);
    }
}
