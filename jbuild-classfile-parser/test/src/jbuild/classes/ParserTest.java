package jbuild.classes;

import jbuild.classes.model.ClassFile;
import jbuild.classes.model.ConstPoolInfo;
import jbuild.classes.model.MajorVersion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class ParserTest {

    JBuildClassFileParser parser = new JBuildClassFileParser();

    @Test
    public void canParseHelloWorldClassFile() throws Exception {
        ClassFile classFile;
        try (var stream = ParserTest.class.getResourceAsStream("/HelloWorld.class")) {
            classFile = parser.parse(stream);
        }

        assertThat(classFile.minorVersion).isEqualTo((short) 0);
        assertThat(classFile.majorVersion.toKnownVersion())
                .isPresent().get()
                .isEqualTo(MajorVersion.Known.V_17);
        assertThat(classFile.constPoolEntries).hasSize(29);

        /*
         * Constant pool: #1 = Methodref #2.#3 // java/lang/Object."<init>":()V #2 =
         * Class #4 // java/lang/Object #3 = NameAndType #5:#6 // "<init>":()V #4 = Utf8
         * java/lang/Object #5 = Utf8 <init> #6 = Utf8 ()V #7 = Fieldref #8.#9 //
         * java/lang/System.out:Ljava/io/PrintStream; #8 = Class #10 // java/lang/System
         * #9 = NameAndType #11:#12 // out:Ljava/io/PrintStream; #10 = Utf8
         * java/lang/System #11 = Utf8 out #12 = Utf8 Ljava/io/PrintStream; #13 = String
         * #14 // Hello world #14 = Utf8 Hello world #15 = Methodref #16.#17 //
         * java/io/PrintStream.println:(Ljava/lang/String;)V #16 = Class #18 //
         * java/io/PrintStream #17 = NameAndType #19:#20 //
         * println:(Ljava/lang/String;)V #18 = Utf8 java/io/PrintStream #19 = Utf8
         * println #20 = Utf8 (Ljava/lang/String;)V #21 = Class #22 // HelloWorld #22 =
         * Utf8 HelloWorld #23 = Utf8 Code #24 = Utf8 LineNumberTable #25 = Utf8 main
         * #26 = Utf8 ([Ljava/lang/String;)V #27 = Utf8 SourceFile #28 = Utf8
         * HelloWorld.java
         */
        List<Class<? extends ConstPoolInfo>> constPoolInfo = classFile.constPoolEntries.stream()
                .map(ConstPoolInfo::getClass)
                .collect(Collectors.toList());
        assertThat(constPoolInfo)
                .containsExactly(JBuildClassFileParser.FIRST_ITEM_SENTINEL.getClass(),
                        ConstPoolInfo.MethodRef.class,
                        ConstPoolInfo.Class.class,
                        ConstPoolInfo.NameAndType.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.FieldRef.class,
                        ConstPoolInfo.Class.class,
                        ConstPoolInfo.NameAndType.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.String.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.MethodRef.class,
                        ConstPoolInfo.Class.class,
                        ConstPoolInfo.NameAndType.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.Class.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.Utf8.class);

        assertThat(classFile.constPoolEntries.get(0)).isSameAs(JBuildClassFileParser.FIRST_ITEM_SENTINEL);
        assertThat((ConstPoolInfo.MethodRef) classFile.constPoolEntries.get(1))
                .extracting(m -> m.classIndex)
                .isEqualTo((short) 2);
        assertThat((ConstPoolInfo.MethodRef) classFile.constPoolEntries.get(1))
                .extracting(m -> m.nameAndTypeIndex)
                .isEqualTo((short) 3);
        assertThat((ConstPoolInfo.Class) classFile.constPoolEntries.get(2))
                .extracting(m -> m.nameIndex)
                .isEqualTo((short) 4);
        assertThat((ConstPoolInfo.NameAndType) classFile.constPoolEntries.get(3))
                .extracting(m -> m.nameIndex)
                .isEqualTo((short) 5);
        assertThat((ConstPoolInfo.NameAndType) classFile.constPoolEntries.get(3))
                .extracting(m -> m.descriptorIndex)
                .isEqualTo((short) 6);
        assertThat((ConstPoolInfo.Utf8) classFile.constPoolEntries.get(4))
                .extracting(m -> m.value)
                .isEqualTo("java/lang/Object".getBytes(UTF_8));
        assertThat((ConstPoolInfo.Utf8) classFile.constPoolEntries.get(5))
                .extracting(m -> m.value)
                .isEqualTo("<init>".getBytes(UTF_8));
        assertThat((ConstPoolInfo.Utf8) classFile.constPoolEntries.get(6))
                .extracting(m -> m.value)
                .isEqualTo("()V".getBytes(UTF_8));
        assertThat((ConstPoolInfo.FieldRef) classFile.constPoolEntries.get(7))
                .extracting(m -> m.classIndex)
                .isEqualTo((short) 8);
        assertThat((ConstPoolInfo.FieldRef) classFile.constPoolEntries.get(7))
                .extracting(m -> m.nameAndTypeIndex)
                .isEqualTo((short) 9);
        assertThat((ConstPoolInfo.Class) classFile.constPoolEntries.get(8))
                .extracting(m -> m.nameIndex)
                .isEqualTo((short) 10);
        assertThat((ConstPoolInfo.NameAndType) classFile.constPoolEntries.get(9))
                .extracting(m -> m.nameIndex)
                .isEqualTo((short) 11);
        assertThat((ConstPoolInfo.NameAndType) classFile.constPoolEntries.get(9))
                .extracting(m -> m.descriptorIndex)
                .isEqualTo((short) 12);
        assertThat((ConstPoolInfo.Utf8) classFile.constPoolEntries.get(10))
                .extracting(m -> m.value)
                .isEqualTo("java/lang/System".getBytes(UTF_8));
        assertThat((ConstPoolInfo.Utf8) classFile.constPoolEntries.get(11))
                .extracting(m -> m.value)
                .isEqualTo("out".getBytes(UTF_8));
        assertThat((ConstPoolInfo.Utf8) classFile.constPoolEntries.get(12))
                .extracting(m -> m.value)
                .isEqualTo("Ljava/io/PrintStream;".getBytes(UTF_8));
        assertThat((ConstPoolInfo.String) classFile.constPoolEntries.get(13))
                .extracting(m -> m.stringIndex)
                .isEqualTo((short) 14);
        assertThat((ConstPoolInfo.Utf8) classFile.constPoolEntries.get(14))
                .extracting(m -> m.value)
                .isEqualTo("Hello world".getBytes(UTF_8));
        assertThat((ConstPoolInfo.MethodRef) classFile.constPoolEntries.get(15))
                .extracting(m -> m.classIndex)
                .isEqualTo((short) 16);
        assertThat((ConstPoolInfo.MethodRef) classFile.constPoolEntries.get(15))
                .extracting(m -> m.nameAndTypeIndex)
                .isEqualTo((short) 17);
        assertThat((ConstPoolInfo.Class) classFile.constPoolEntries.get(16))
                .extracting(m -> m.nameIndex)
                .isEqualTo((short) 18);
        assertThat((ConstPoolInfo.NameAndType) classFile.constPoolEntries.get(17))
                .extracting(m -> m.nameIndex)
                .isEqualTo((short) 19);
        assertThat((ConstPoolInfo.NameAndType) classFile.constPoolEntries.get(17))
                .extracting(m -> m.descriptorIndex)
                .isEqualTo((short) 20);
        assertThat((ConstPoolInfo.Utf8) classFile.constPoolEntries.get(18))
                .extracting(m -> m.value)
                .isEqualTo("java/io/PrintStream".getBytes(UTF_8));
        assertThat((ConstPoolInfo.Utf8) classFile.constPoolEntries.get(19))
                .extracting(m -> m.value)
                .isEqualTo("println".getBytes(UTF_8));
        assertThat((ConstPoolInfo.Utf8) classFile.constPoolEntries.get(20))
                .extracting(m -> m.value)
                .isEqualTo("(Ljava/lang/String;)V".getBytes(UTF_8));
        assertThat((ConstPoolInfo.Class) classFile.constPoolEntries.get(21))
                .extracting(m -> m.nameIndex)
                .isEqualTo((short) 22);
        assertThat((ConstPoolInfo.Utf8) classFile.constPoolEntries.get(22))
                .extracting(m -> m.value)
                .isEqualTo("HelloWorld".getBytes(UTF_8));
        assertThat((ConstPoolInfo.Utf8) classFile.constPoolEntries.get(23))
                .extracting(m -> m.value)
                .isEqualTo("Code".getBytes(UTF_8));
        assertThat((ConstPoolInfo.Utf8) classFile.constPoolEntries.get(24))
                .extracting(m -> m.value)
                .isEqualTo("LineNumberTable".getBytes(UTF_8));
        assertThat((ConstPoolInfo.Utf8) classFile.constPoolEntries.get(25))
                .extracting(m -> m.value)
                .isEqualTo("main".getBytes(UTF_8));
        assertThat((ConstPoolInfo.Utf8) classFile.constPoolEntries.get(26))
                .extracting(m -> m.value)
                .isEqualTo("([Ljava/lang/String;)V".getBytes(UTF_8));
        assertThat((ConstPoolInfo.Utf8) classFile.constPoolEntries.get(27))
                .extracting(m -> m.value)
                .isEqualTo("SourceFile".getBytes(UTF_8));
        assertThat((ConstPoolInfo.Utf8) classFile.constPoolEntries.get(28))
                .extracting(m -> m.value)
                .isEqualTo("HelloWorld.java".getBytes(UTF_8));

        assertClassAccessFlags(classFile.accessFlags, true, false, true, false, false, false, false, false, false);

        assertThat(classFile.thisClass).isEqualTo((short) 21);
        assertThat(classFile.superClass).isEqualTo((short) 2);
        assertThat(classFile.interfaces).isEmpty();
        assertThat(classFile.fields).isEmpty();

        assertThat(classFile.methods).hasSize(2);

        assertMethodAccessFlags(classFile.methods.get(0).accessFlags, true, false, false, false, false, false,
                false, false, false, false, false, false);
        assertThat(classFile.methods.get(0).nameIndex).isEqualTo((short) 5);
        assertThat(classFile.methods.get(0).descriptorIndex).isEqualTo((short) 6);
        assertThat(classFile.methods.get(0).attributes).hasSize(1);
        assertThat(classFile.methods.get(0).attributes.get(0).nameIndex).isEqualTo((short) 23);

        assertMethodAccessFlags(classFile.methods.get(1).accessFlags, true, false, false, true, false, false,
                false, false, false, false, false, false);
        assertThat(classFile.methods.get(1).nameIndex).isEqualTo((short) 25);
        assertThat(classFile.methods.get(1).descriptorIndex).isEqualTo((short) 26);
        assertThat(classFile.methods.get(1).attributes).hasSize(1);
        assertThat(classFile.methods.get(1).attributes.get(0).nameIndex).isEqualTo((short) 23);

        assertThat(classFile.attributes).hasSize(1);
        assertThat(classFile.attributes.get(0).nameIndex).isEqualTo((short) 27);
    }

    @Test
    void canFindTypesReferredTo() throws Exception {
        ClassFile classFile;
        try (var stream = ParserTest.class.getResourceAsStream("/HelloWorld.class")) {
            classFile = parser.parse(stream);
        }

        assertThat(classFile.getTypesReferredTo())
                .containsExactlyInAnyOrder("java/io/PrintStream");
    }

    private void assertClassAccessFlags(short flags, boolean isPublic, boolean isFinal, boolean isSuper,
                                        boolean isInterface, boolean isAbstract, boolean isSynthetic,
                                        boolean isAnnotation, boolean isEnum, boolean isModule) {
        assertThat(flags & 0x01).isEqualTo(isPublic ? 0x01 : 0);
        assertThat(flags & 0x10).isEqualTo(isFinal ? 0x10 : 0);
        assertThat(flags & 0x20).isEqualTo(isSuper ? 0x20 : 0);
        assertThat(flags & 0x200).isEqualTo(isInterface ? 0x200 : 0);
        assertThat(flags & 0x400).isEqualTo(isAbstract ? 0x400 : 0);
        assertThat(flags & 0x1000).isEqualTo(isSynthetic ? 0x1000 : 0);
        assertThat(flags & 0x2000).isEqualTo(isAnnotation ? 0x2000 : 0);
        assertThat(flags & 0x4000).isEqualTo(isEnum ? 0x4000 : 0);
        assertThat(flags & 0x8000).isEqualTo(isModule ? 0x8000 : 0);
    }

    private void assertMethodAccessFlags(short flags, boolean isPublic, boolean isPrivate, boolean isProtected,
                                         boolean isStatic, boolean isFinal, boolean isSynchronized,
                                         boolean isBridge, boolean isVarargs, boolean isNative, boolean isAbstract,
                                         boolean isStrict, boolean isSynthetic) {
        assertThat(flags & 0x01).isEqualTo(isPublic ? 0x01 : 0);
        assertThat(flags & 0x02).isEqualTo(isPrivate ? 0x02 : 0);
        assertThat(flags & 0x04).isEqualTo(isProtected ? 0x04 : 0);
        assertThat(flags & 0x08).isEqualTo(isStatic ? 0x08 : 0);
        assertThat(flags & 0x10).isEqualTo(isFinal ? 0x10 : 0);
        assertThat(flags & 0x20).isEqualTo(isSynchronized ? 0x20 : 0);
        assertThat(flags & 0x40).isEqualTo(isBridge ? 0x40 : 0);
        assertThat(flags & 0x80).isEqualTo(isVarargs ? 0x80 : 0);
        assertThat(flags & 0x100).isEqualTo(isNative ? 0x100 : 0);
        assertThat(flags & 0x400).isEqualTo(isAbstract ? 0x400 : 0);
        assertThat(flags & 0x800).isEqualTo(isStrict ? 0x800 : 0);
        assertThat(flags & 0x1000).isEqualTo(isSynthetic ? 0x1000 : 0);
    }
}
