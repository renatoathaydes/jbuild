package jbuild.classes;

import jbuild.classes.model.ClassFile;
import jbuild.classes.model.ConstPoolInfo;
import jbuild.classes.model.MajorVersion;
import jbuild.classes.model.attributes.AnnotationInfo;
import jbuild.classes.model.attributes.ElementValuePair;
import jbuild.classes.model.attributes.MethodParameter;
import jbuild.classes.signature.JavaTypeSignature;
import jbuild.classes.signature.JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature;
import jbuild.classes.signature.MethodSignature;
import jbuild.classes.signature.MethodSignature.MethodResult.VoidDescriptor;
import jbuild.classes.signature.SimpleClassTypeSignature;
import jbuild.classes.signature.SimpleClassTypeSignature.TypeArgument;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class JBuildClassFileParserTest {

    JBuildClassFileParser parser = new JBuildClassFileParser();

    @Test
    public void canParseHelloWorldClassFile() throws Exception {
        ClassFile classFile = parseHelloWorldClass();

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
                        ConstPoolInfo.ConstClass.class,
                        ConstPoolInfo.NameAndType.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.FieldRef.class,
                        ConstPoolInfo.ConstClass.class,
                        ConstPoolInfo.NameAndType.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.ConstString.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.MethodRef.class,
                        ConstPoolInfo.ConstClass.class,
                        ConstPoolInfo.NameAndType.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.Utf8.class,
                        ConstPoolInfo.ConstClass.class,
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
        assertThat((ConstPoolInfo.ConstClass) classFile.constPoolEntries.get(2))
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
        assertThat((ConstPoolInfo.ConstClass) classFile.constPoolEntries.get(8))
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
        assertThat((ConstPoolInfo.ConstString) classFile.constPoolEntries.get(13))
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
        assertThat((ConstPoolInfo.ConstClass) classFile.constPoolEntries.get(16))
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
        assertThat((ConstPoolInfo.ConstClass) classFile.constPoolEntries.get(21))
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
        ClassFile classFile = parseHelloWorldClass();

        assertThat(classFile.getTypeName())
                .isEqualTo("LHelloWorld;");
        assertThat(classFile.getTypesReferredTo())
                .containsExactlyInAnyOrder("()V",
                        "Ljava/io/PrintStream;", "(Ljava/lang/String;)V");

        assertThat(classFile.getRuntimeVisibleAnnotations()).isEmpty();
        assertThat(classFile.getRuntimeInvisibleAnnotations()).isEmpty();
    }

    @Test
    void canParseAnnotations() throws Exception {
        ClassFile classFile = parseExampleAnnotatedClass();

        assertThat(classFile.getTypeName())
                .isEqualTo("Ljbuild/api/ExampleAnnotated;");
        assertThat(classFile.getTypesReferredTo()).containsExactly("()V");
        assertThat(classFile.getRuntimeVisibleAnnotations()).isEmpty();
        assertThat(classFile.getRuntimeInvisibleAnnotations()).hasSize(1);

        var annotation = classFile.getRuntimeInvisibleAnnotations().get(0);

        assertThat(annotation.typeDescriptor).isEqualTo("Ljbuild/api/JbTaskInfo;");
        assertThat(annotation.elementValuePairs).hasSize(3);
        assertThat(annotation.elementValuePairs.get(0))
                .isEqualTo(new ElementValuePair("name", ElementValuePair.Type.STRING, "my-custom-task"));
        assertThat(annotation.elementValuePairs.get(1))
                .isEqualTo(new ElementValuePair("inputs", ElementValuePair.Type.ARRAY,
                        List.of("*.txt", "*.json")));
        assertThat(annotation.elementValuePairs.get(2))
                .isEqualTo(new ElementValuePair("phase", ElementValuePair.Type.ANNOTATION,
                        new AnnotationInfo("Ljbuild/api/CustomTaskPhase;", List.of(
                                new ElementValuePair("index", ElementValuePair.Type.INT, 42),
                                new ElementValuePair("name", ElementValuePair.Type.STRING, "my-custom-phase")
                        ))));
    }

    @Test
    void canFindClassConstructors() throws IOException {
        var stringType = new ClassTypeSignature("java.lang", new SimpleClassTypeSignature("String"));

        ClassFile classFile = parseMultiConstructorsClass();

        assertThat(classFile.getTypeName())
                .isEqualTo("Lmain/MultiConstructors;");

        var constructors = classFile.getConstructors();
        assertThat(constructors).hasSize(2);
        assertThat(classFile.getMethodTypeDescriptor(constructors.get(0)))
                .isEqualTo(new MethodSignature(List.of(stringType),
                        VoidDescriptor.INSTANCE)); // (Ljava/lang/String;)V
        // only generic methods have a Signature attribute
        assertThat(classFile.getSignatureAttribute(constructors.get(0)))
                .isNotPresent();
        assertThat(classFile.getMethodParameters(constructors.get(0)))
                .isEqualTo(List.of(new MethodParameter(MethodParameter.AccessFlag.NONE, "hello")));

        // String foo, final boolean bar, List<String> strings
        // (Ljava/lang/String;ZLjava/util/List<Ljava/lang/String;>;)V
        var constructorSignature = new MethodSignature(List.of(), List.of(
                stringType,
                JavaTypeSignature.BaseType.Z,
                new ClassTypeSignature("java.util",
                        new SimpleClassTypeSignature("List", List.of(
                                new TypeArgument.Reference(stringType))))),
                VoidDescriptor.INSTANCE,
                List.of());

        assertThat(classFile.getMethodTypeDescriptor(constructors.get(1)))
                .isEqualTo(new MethodSignature(List.of(stringType,
                        JavaTypeSignature.BaseType.Z,
                        new ClassTypeSignature("java.util", new SimpleClassTypeSignature("List"))),
                        VoidDescriptor.INSTANCE)); // (Ljava/lang/String;ZLjava/util/List;)V
        assertThat(classFile.getSignatureAttribute(constructors.get(1)))
                .isPresent()
                .get()
                .isEqualTo(constructorSignature);

        assertThat(classFile.getMethodParameters(constructors.get(1)))
                .isEqualTo(List.of(
                        new MethodParameter(MethodParameter.AccessFlag.NONE, "foo"),
                        new MethodParameter(MethodParameter.AccessFlag.ACC_FINAL, "bar"),
                        new MethodParameter(MethodParameter.AccessFlag.NONE, "strings")));
    }

    @Test
    void canParseDifficultClass() throws IOException {
        ClassFile classFile = parseDifficultClass();

        assertThat(classFile.getTypeName())
                .isEqualTo("Ljbuild/util/AsyncUtils;");
    }

    @Test
    void canParseReallyDifficultClass() throws IOException {
        ClassFile classFile = parseReallyDifficultClass();

        assertThat(classFile.getTypeName())
                .isEqualTo("Ljbuild/artifact/http/DefaultHttpClient;");
        assertThat(classFile.getTypesReferredTo()).containsExactlyInAnyOrder(
                "Ljava/time/Duration;",
                "Ljava/net/http/HttpClient;",
                "(J)Ljava/time/Duration;",
                "()V",
                "(Ljava/time/Duration;)Ljava/net/http/HttpClient$Builder;",
                "Ljava/net/http/HttpClient$Builder;",
                "(Ljava/net/http/HttpClient$Redirect;)Ljava/net/http/HttpClient$Builder;",
                "()Ljava/net/http/HttpClient;",
                "Ljava/net/http/HttpClient$Redirect;",
                "Ljbuild/artifact/http/DefaultHttpClient$Singleton;",
                "()Ljava/net/http/HttpClient$Builder;");
    }

    private ClassFile parseHelloWorldClass() throws IOException {
        return parseClass("/HelloWorld.cls");
    }

    private ClassFile parseMultiConstructorsClass() throws IOException {
        return parseClass("/MultiConstructors.cls");
    }

    private ClassFile parseDifficultClass() throws IOException {
        return parseClass("/AsyncUtils.cls");
    }

    private ClassFile parseReallyDifficultClass() throws IOException {
        return parseClass("/DefaultHttpClient.cls");
    }

    private ClassFile parseExampleAnnotatedClass() throws IOException {
        return parseClass("/ExampleAnnotated.cls");
    }

    private ClassFile parseClass(String path) throws IOException {
        ClassFile classFile;
        try (var stream = JBuildClassFileParserTest.class.getResourceAsStream(path)) {
            classFile = parser.parse(stream);
        }
        return classFile;
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
