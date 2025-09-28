package jbuild.classes.parser;

import jbuild.classes.ClassFileException;
import jbuild.classes.model.ClassFile;
import jbuild.classes.model.ConstPoolInfo;
import jbuild.classes.model.FieldInfo;
import jbuild.classes.model.MethodInfo;
import jbuild.classes.model.attributes.AttributeInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A Java Language class file parser.
 */
public class JBuildClassFileParser {

    /**
     * Always the first item in the constant pool.
     */
    public static final ConstPoolInfo.Utf8 FIRST_ITEM_SENTINEL = new ConstPoolInfo.Utf8(new byte[0]);

    public ClassFile parse(InputStream input) throws IOException {
        var scanner = new ByteScanner(input);
        try {
            return parse(scanner);
        } catch (ClassFileException e) {
            throw e;
        } catch (Exception e) {
            throw new ClassFileException("Unexpected error parsing class file", e, scanner.previousPosition());
        }
    }

    /**
     * Parse a class file's bytes provided by the given scanner.
     * <p>
     * Class file specification as described in:
     * <a href="https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html">The class File Format</a>.
     *
     * <pre>
     *  ClassFile {
     *     u4             magic;
     *     u2             minor_version;
     *     u2             major_version;
     *     u2             constant_pool_count;
     *     cp_info        constant_pool[constant_pool_count-1];
     *     u2             access_flags;
     *     u2             this_class;
     *     u2             super_class;
     *     u2             interfaces_count;
     *     u2             interfaces[interfaces_count];
     *     u2             fields_count;
     *     field_info     fields[fields_count];
     *     u2             methods_count;
     *     method_info    methods[methods_count];
     *     u2             attributes_count;
     *     attribute_info attributes[attributes_count];
     * }
     * </pre>
     *
     * @param scanner class file bytes scanner
     * @return the class file
     */
    private ClassFile parse(ByteScanner scanner) {
        var magic = scanner.nextInt();
        if (magic != ClassFile.MAGIC) {
            throw new ClassFileException("Not a Java class file (missing magic number)", 0);
        }

        var minor = scanner.nextShort();
        var major = scanner.nextShort();
        var constPool = parseConstPool(scanner, scanner.nextShortIndex());
        var accessFlags = scanner.nextShort();
        var thisClass = scanner.nextShort();
        var superClass = scanner.nextShort();
        var interfaces = parseInterfaces(scanner, scanner.nextShortIndex());
        var fields = parseFields(scanner, scanner.nextShortIndex());
        var methods = parseMethods(scanner, scanner.nextShortIndex());
        var attributes = parseAttributes(scanner, scanner.nextShortIndex());

        return new ClassFile(minor, major, constPool, accessFlags, thisClass, superClass, interfaces,
                fields, methods, attributes);
    }

    private List<ConstPoolInfo> parseConstPool(ByteScanner scanner, int constPoolCount) {
        // The value of the constant_pool_count item is equal to the number of entries in the constant_pool table plus one
        var result = new ArrayList<ConstPoolInfo>(constPoolCount);
        for (var i = 0; i < constPoolCount; i++) {
            result.add(null);
        }
        // first item is always a dummy value, so the rest of the items fall into the appropriate index
        result.set(0, FIRST_ITEM_SENTINEL);
        for (var i = 1; i < constPoolCount; i++) {
            var info = parseConstPoolInfo(scanner);
            result.set(i, info);
            final var tag = info.tag;
            if (tag == ConstPoolInfo.ConstLong.TAG ||
                    tag == ConstPoolInfo.ConstDouble.TAG) {
                // All 8-byte constants take up two entries in the constant_pool table
                i++;
            }
        }
        return result;
    }

    private ConstPoolInfo parseConstPoolInfo(ByteScanner scanner) {
        var tag = scanner.nextByte();
        switch (tag) {
            case ConstPoolInfo.ConstClass.TAG:
                return new ConstPoolInfo.ConstClass(scanner.nextShort());
            case ConstPoolInfo.FieldRef.TAG:
                return new ConstPoolInfo.FieldRef(scanner.nextShort(), scanner.nextShort());
            case ConstPoolInfo.MethodRef.TAG:
                return new ConstPoolInfo.MethodRef(scanner.nextShort(), scanner.nextShort());
            case ConstPoolInfo.InterfaceMethodRef.TAG:
                return new ConstPoolInfo.InterfaceMethodRef(scanner.nextShort(), scanner.nextShort());
            case ConstPoolInfo.ConstString.TAG:
                return new ConstPoolInfo.ConstString(scanner.nextShort());
            case ConstPoolInfo.ConstInt.TAG:
                return new ConstPoolInfo.ConstInt(scanner.nextInt());
            case ConstPoolInfo.ConstFloat.TAG:
                return new ConstPoolInfo.ConstFloat(scanner.nextFloat());
            case ConstPoolInfo.ConstLong.TAG:
                return new ConstPoolInfo.ConstLong(scanner.nextLong());
            case ConstPoolInfo.ConstDouble.TAG:
                return new ConstPoolInfo.ConstDouble(scanner.nextDouble());
            case ConstPoolInfo.NameAndType.TAG:
                return new ConstPoolInfo.NameAndType(scanner.nextShort(), scanner.nextShort());
            case ConstPoolInfo.Utf8.TAG: {
                var length = scanner.nextShort() & 0xFFFF;
                var contents = new byte[length];
                scanner.next(contents);
                return new ConstPoolInfo.Utf8(contents);
            }
            case ConstPoolInfo.MethodHandle.TAG:
                return new ConstPoolInfo.MethodHandle(scanner.nextByte(), scanner.nextShort());
            case ConstPoolInfo.MethodType.TAG:
                return new ConstPoolInfo.MethodType(scanner.nextShort());
            case ConstPoolInfo.DynamicInfo.TAG:
                return new ConstPoolInfo.DynamicInfo(scanner.nextShort(), scanner.nextShort());
            case ConstPoolInfo.InvokeDynamic.TAG:
                return new ConstPoolInfo.InvokeDynamic(scanner.nextShort(), scanner.nextShort());
            case ConstPoolInfo.ModuleInfo.TAG:
                return new ConstPoolInfo.ModuleInfo(scanner.nextShort());
            case ConstPoolInfo.PackageInfo.TAG:
                return new ConstPoolInfo.PackageInfo(scanner.nextShort());
            default:
                throw new ClassFileException("Unknown constant pool tag: " + tag, scanner.previousPosition());
        }
    }

    private short[] parseInterfaces(ByteScanner scanner, int interfaceCount) {
        var result = new short[interfaceCount];
        for (var i = 0; i < interfaceCount; i++) {
            result[i] = scanner.nextShort();
        }
        return result;
    }

    private List<FieldInfo> parseFields(ByteScanner scanner, int interfaceCount) {
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
    private FieldInfo parseField(ByteScanner scanner) {
        return new FieldInfo(scanner.nextShort(),
                scanner.nextShort(),
                scanner.nextShort(),
                parseAttributes(scanner, scanner.nextShortIndex()));
    }

    private List<MethodInfo> parseMethods(ByteScanner scanner, int length) {
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
    private MethodInfo parseMethod(ByteScanner scanner) {
        return new MethodInfo(scanner.nextShort(),
                scanner.nextShort(),
                scanner.nextShort(),
                parseAttributes(scanner, scanner.nextShortIndex()));
    }

    /**
     * attribute_info {
     * u2 attribute_name_index;
     * u4 attribute_length;
     * u1 info[attribute_length];
     * }
     */
    private List<AttributeInfo> parseAttributes(ByteScanner scanner, int length) {
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

