package jbuild.classes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import jbuild.classes.model.ClassFile;
import jbuild.classes.model.ConstPoolInfo;
import jbuild.classes.model.MajorVersion;

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
        assertThat(classFile.constPoolEntries).hasSize(28);

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
        List<Class<? extends ConstPoolInfo>> classes = classFile.constPoolEntries.stream()
                .map(ConstPoolInfo::getClass)
                .collect(Collectors.toList());
        assertThat(classes)
                .contains(ConstPoolInfo.MethodRef.class,
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
    }

}
