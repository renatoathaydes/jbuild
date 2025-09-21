package jbuild.classes.parser;

import jbuild.classes.model.ClassFile;
import jbuild.classes.model.ConstPoolInfo;

import java.util.Optional;

abstract class AbstractAttributeParser {

    private final ClassFile classFile;

    public AbstractAttributeParser(ClassFile classFile) {
        this.classFile = classFile;
    }

    protected String nextConstUf8(ByteScanner scanner) {
        return constUtf8(scanner.nextShortIndex());
    }

    protected String constUtf8(int index) {
        return constUtf8(classFile, index);
    }

    public static String constUtf8(ClassFile classFile, int index) {
        var utf8 = (ConstPoolInfo.Utf8) classFile.constPoolEntries.get(index);
        return utf8.asString();
    }

    protected int nextConstInt(ByteScanner scanner) {
        var i = (ConstPoolInfo.ConstInt) classFile.constPoolEntries.get(scanner.nextShortIndex());
        return i.value;
    }

    protected double nextConstDouble(ByteScanner scanner) {
        var i = (ConstPoolInfo.ConstDouble) classFile.constPoolEntries.get(scanner.nextShortIndex());
        return i.value;
    }

    protected float nextConstFloat(ByteScanner scanner) {
        var i = (ConstPoolInfo.ConstFloat) classFile.constPoolEntries.get(scanner.nextShortIndex());
        return i.value;
    }

    protected long nextConstLong(ByteScanner scanner) {
        var i = (ConstPoolInfo.ConstLong) classFile.constPoolEntries.get(scanner.nextShortIndex());
        return i.value;
    }

    protected String nextConstClass(ByteScanner scanner) {
        var i = (ConstPoolInfo.ConstClass) classFile.constPoolEntries.get(scanner.nextShortIndex());
        return constUtf8(i.nameIndex);
    }

    protected String nextConstPackage(ByteScanner scanner) {
        var i = (ConstPoolInfo.PackageInfo) classFile.constPoolEntries.get(scanner.nextShortIndex());
        return constUtf8(i.nameIndex);
    }

    protected String nextConstModule(ByteScanner scanner) {
        var i = (ConstPoolInfo.ModuleInfo) classFile.constPoolEntries.get(scanner.nextShortIndex());
        return constUtf8(i.nameIndex);
    }

    protected Optional<ConstPoolInfo.NameAndType> nextConstNameAndType(ByteScanner scanner) {
        var methodIndex = scanner.nextShortIndex();
        if (methodIndex == 0) return Optional.empty();
        return Optional.of((ConstPoolInfo.NameAndType) classFile.constPoolEntries.get(methodIndex));
    }
}
