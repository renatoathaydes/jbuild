package jbuild.classes.model;

import jbuild.classes.ByteScanner;

public abstract class AbstractAttributeParser {

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
}
