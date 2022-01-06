package jbuild.java;

import jbuild.java.code.Code;
import jbuild.java.code.Definition;
import jbuild.log.JBuildLog;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static jbuild.TestSystemProperties.myClassesJar;
import static jbuild.TestSystemProperties.osgiaasCliApiJar;
import static jbuild.TestSystemProperties.otherClassesJar;
import static org.assertj.core.api.Assertions.assertThat;

public class JavapOutputParserTest {

    @Test
    void canParseBasicClass() {
        var out = new ByteArrayOutputStream();
        var parser = new JavapOutputParser(new JBuildLog(new PrintStream(out), false));
        var types = parser.processJavapOutput(javap(myClassesJar, "Hello"));
        var result = types.get("LHello;");

        assertThat(result.typeName).isEqualTo("LHello;");
        assertThat(result.implementedInterfaces).isEmpty();
        assertThat(result.type.getParentTypes()).isEmpty();

        assertThat(result.fields).containsExactlyInAnyOrderElementsOf(Set.of(
                new Definition.FieldDefinition("isOk", "Z"),
                new Definition.FieldDefinition("message", "Ljava/lang/String;"),
                new Definition.FieldDefinition("CONST", "Ljava/lang/String;"),
                new Definition.FieldDefinition("aFloat", "F"),
                new Definition.FieldDefinition("protectedInt", "I")
        ));

        assertThat(result.methodHandles).isEmpty();

        assertThat(result.methods.keySet())
                .containsExactlyInAnyOrderElementsOf(Set.of(
                        new Definition.MethodDefinition("\"<init>\"", "(Ljava/lang/String;)V"),
                        new Definition.MethodDefinition("foo", "()Z"),
                        new Definition.MethodDefinition("aPrivateMethod", "()V"),
                        new Definition.MethodDefinition("theFloat", "(FJ)F"),
                        new Definition.MethodDefinition("getMessage", "()Ljava/lang/String;")));

        assertThat(result.methods.get(new Definition.MethodDefinition("\"<init>\"", "(Ljava/lang/String;)V"))).isEmpty();
        assertThat(result.methods.get(new Definition.MethodDefinition("foo", "()Z"))).isEmpty();
        assertThat(result.methods.get(new Definition.MethodDefinition("aPrivateMethod", "()V"))).isEmpty();
        assertThat(result.methods.get(new Definition.MethodDefinition("theFloat", "(FJ)F"))).isEmpty();
        assertThat(result.methods.get(new Definition.MethodDefinition("getMessage", "()Ljava/lang/String;"))).isEmpty();
    }

    @Test
    void canParseClassWithStaticBlockAndStaticMethods() {
        var out = new ByteArrayOutputStream();
        var parser = new JavapOutputParser(new JBuildLog(new PrintStream(out), false));
        var types = parser.processJavapOutput(javap(myClassesJar, "foo.Bar"));
        var result = types.get("Lfoo/Bar;");

        assertThat(result.typeName).isEqualTo("Lfoo/Bar;");
        assertThat(result.implementedInterfaces).isEmpty();
        assertThat(result.type.getParentTypes()).isEmpty();

        assertThat(result.fields).isEmpty();
        assertThat(result.methodHandles).isEmpty();
        assertThat(result.methods.keySet()).containsExactlyInAnyOrderElementsOf(Set.of(new Definition.MethodDefinition("\"<init>\"", "()V")));
        assertThat(result.methods.get(new Definition.MethodDefinition("\"<init>\"", "()V"))).isEmpty();

        types = parser.processJavapOutput(javap(myClassesJar, "foo.Zort"));
        result = types.get("Lfoo/Zort;");

        assertThat(result.typeName).isEqualTo("Lfoo/Zort;");
        assertThat(result.implementedInterfaces).isEmpty();
        assertThat(result.type.getParentTypes()).isEmpty();

        assertThat(result.fields).containsExactlyInAnyOrderElementsOf(Set.of(new Definition.FieldDefinition("bar", "Lfoo/Bar;")));

        assertThat(result.methods.keySet()).containsExactlyInAnyOrderElementsOf(Set.of(
                new Definition.MethodDefinition("static{}", "()V"),
                new Definition.MethodDefinition("getBar", "(Lfoo/Bar;)Lfoo/Bar;"),
                new Definition.MethodDefinition("createBar", "()Lfoo/Bar;"),
                new Definition.MethodDefinition("\"<init>\"", "()V")
        ));
        assertThat(result.methods.get(new Definition.MethodDefinition("static{}", "()V")))
                .containsExactlyInAnyOrderElementsOf(Set.of(
                        new Code.Type("Lfoo/Bar;"),
                        new Code.Method("Lfoo/Bar;", "\"<init>\"", "()V")
                ));
    }

    @Test
    void canParseBasicEnum() {
        var out = new ByteArrayOutputStream();
        var parser = new JavapOutputParser(new JBuildLog(new PrintStream(out), false));
        var types = parser.processJavapOutput(javap(myClassesJar, "foo.SomeEnum"));
        var result = types.get("Lfoo/SomeEnum;");

        assertThat(result.typeName).isEqualTo("Lfoo/SomeEnum;");
        assertThat(result.implementedInterfaces).isEmpty();
        assertThat(result.type.superTypes)
                .isEqualTo(List.of(new JavaType.TypeBound("Ljava/lang/Enum;",
                        List.of(new JavaType.TypeParam("Lfoo/SomeEnum;", List.of(), List.of())))));
        assertThat(result.type.interfaces).isEmpty();

        assertThat(result.fields).containsExactlyInAnyOrderElementsOf(Set.of(
                new Definition.FieldDefinition("$VALUES", "[Lfoo/SomeEnum;"),
                new Definition.FieldDefinition("SOMETHING", "Lfoo/SomeEnum;"),
                new Definition.FieldDefinition("NOTHING", "Lfoo/SomeEnum;"))
        );

        assertThat(result.methodHandles).isEmpty();

        assertThat(result.methods.keySet()).containsExactlyInAnyOrderElementsOf(Set.of(
                new Definition.MethodDefinition("\"<init>\"", "(Ljava/lang/String;I)V"),
                new Definition.MethodDefinition("values", "()[Lfoo/SomeEnum;"),
                new Definition.MethodDefinition("valueOf", "(Ljava/lang/String;)Lfoo/SomeEnum;"),
                new Definition.MethodDefinition("static{}", "()V"),
                // these two methods are inherited from Enum, but special-cased so we can find references to them
                new Definition.MethodDefinition("ordinal", "()I"),
                new Definition.MethodDefinition("name", "()Ljava/lang/String;")));

        assertThat(result.methods.get(new Definition.MethodDefinition("\"<init>\"", "(Ljava/lang/String;I)V")))
                .isEmpty();

        assertThat(result.methods.get(new Definition.MethodDefinition("values", "()[Lfoo/SomeEnum;")))
                .isEmpty();

        assertThat(result.methods.get(new Definition.MethodDefinition("valueOf", "(Ljava/lang/String;)Lfoo/SomeEnum;")))
                .isEmpty();

        assertThat(result.methods.get(new Definition.MethodDefinition("static{}", "()V")))
                .isEmpty();
    }

    @Test
    void canParseClassExtendingAnother() {
        var out = new ByteArrayOutputStream();
        var parser = new JavapOutputParser(new JBuildLog(new PrintStream(out), false));
        var types = parser.processJavapOutput(javap(myClassesJar, "foo.SomethingSpecific"));
        var result = types.get("Lfoo/SomethingSpecific;");

        assertThat(result.typeName).isEqualTo("Lfoo/SomethingSpecific;");
        assertThat(result.implementedInterfaces).isEmpty();
        assertThat(result.type.superTypes)
                .isEqualTo(List.of(new JavaType.TypeBound("Lfoo/Something;", List.of())));
        assertThat(result.type.interfaces).isEmpty();
        assertThat(result.fields).isEmpty();
        assertThat(result.methodHandles).isEmpty();
        assertThat(result.methods.keySet()).containsExactlyInAnyOrderElementsOf(Set.of(
                new Definition.MethodDefinition("\"<init>\"", "()V")));
    }

    @Test
    void canParseFunctionalCode() {
        var out = new ByteArrayOutputStream();
        var parser = new JavapOutputParser(new JBuildLog(new PrintStream(out), false));
        var types = parser.processJavapOutput(javap(myClassesJar, "foo.FunctionalCode"));
        var result = types.get("Lfoo/FunctionalCode;");

        assertThat(result.typeName).isEqualTo("Lfoo/FunctionalCode;");
        assertThat(result.implementedInterfaces).isEmpty();
        assertThat(result.type.getParentTypes()).isEmpty();

        assertThat(result.fields).containsExactlyInAnyOrderElementsOf(Set.of(new Definition.FieldDefinition("log", "Lfoo/ExampleLogger;")));

        assertThat(result.methodHandles).containsExactlyInAnyOrderElementsOf(Set.of(
                new Code.Method("Lfoo/ExampleLogger;", "debug", "(Ljava/lang/String;)V")
        ));

        assertThat(result.methods.keySet()).containsExactlyInAnyOrderElementsOf(Set.of(
                new Definition.MethodDefinition("lambda$countLengths$0", "(Lfoo/Zort;)Ljava/lang/String;"),
                new Definition.MethodDefinition("lambda$filter$1", "(Lfoo/SomeEnum;)Z"),
                new Definition.MethodDefinition("filter", "(Ljava/util/List;)Ljava/util/List;"),
                new Definition.MethodDefinition("logLengthsStats", "(Ljava/util/List;)V"),
                new Definition.MethodDefinition("countLengths", "(Ljava/util/List;)Ljava/util/IntSummaryStatistics;"),
                new Definition.MethodDefinition("\"<init>\"", "(Lfoo/ExampleLogger;)V")));

        assertThat(result.methods.get(new Definition.MethodDefinition("lambda$countLengths$0", "(Lfoo/Zort;)Ljava/lang/String;")))
                .containsExactlyInAnyOrderElementsOf(Set.of(
                        new Code.Field("Lfoo/Zort;", "bar", "Lfoo/Bar;"),
                        new Code.Method("Lfoo/Zort;", "createBar", "()Lfoo/Bar;")
                ));

        assertThat(result.methods.get(new Definition.MethodDefinition("lambda$filter$1", "(Lfoo/SomeEnum;)Z")))
                .containsExactlyInAnyOrderElementsOf(Set.of(
                        new Code.Field("Lfoo/SomeEnum;", "SOMETHING", "Lfoo/SomeEnum;"),
                        new Code.Method("Lfoo/ExampleLogger;", "info", "(Ljava/lang/String;)V")
                ));

        assertThat(result.methods.get(new Definition.MethodDefinition("filter", "(Ljava/util/List;)Ljava/util/List;")))
                .isEmpty();

        assertThat(result.methods.get(new Definition.MethodDefinition("logLengthsStats", "(Ljava/util/List;)V")))
                .containsExactlyInAnyOrderElementsOf(Set.of(
                        new Code.Method("Lfoo/ExampleLogger;", "info", "(Ljava/lang/String;)V")
                ));

        assertThat(result.methods.get(new Definition.MethodDefinition("countLengths", "(Ljava/util/List;)Ljava/util/IntSummaryStatistics;")))
                .isEmpty();

        assertThat(result.methods.get(new Definition.MethodDefinition("\"<init>\"", "(Lfoo/ExampleLogger;)V")))
                .isEmpty();

    }

    @Test
    void canParseMultipleClassesAtOnce() {
        var out = new ByteArrayOutputStream();
        var parser = new JavapOutputParser(new JBuildLog(new PrintStream(out), false));
        var result = parser.processJavapOutput(
                javap(myClassesJar, "Hello", "foo.EmptyInterface", "foo.FunctionalCode", "foo.Bar")
        ).values();

        assertThat(result.stream().map(c -> c.typeName).collect(toList()))
                .isEqualTo(List.of("LHello;", "Lfoo/EmptyInterface;", "Lfoo/FunctionalCode;", "Lfoo/Bar;"));

        var hello = result.stream().filter(it -> it.typeName.equals("LHello;"))
                .findFirst().orElseThrow();
        var emptyInterface = result.stream().filter(it -> it.typeName.equals("Lfoo/EmptyInterface;"))
                .findFirst().orElseThrow();
        var funCode = result.stream().filter(it -> it.typeName.equals("Lfoo/FunctionalCode;"))
                .findFirst().orElseThrow();
        var bar = result.stream().filter(it -> it.typeName.equals("Lfoo/Bar;"))
                .findFirst().orElseThrow();

        // make sure contents of each class didn't mix up
        assertThat(hello.methods.keySet().stream().map(it -> it.name).collect(toSet()))
                .containsExactlyInAnyOrderElementsOf(Set.of("\"<init>\"", "getMessage", "foo", "theFloat", "aPrivateMethod"));
        assertThat(hello.implementedInterfaces).isEmpty();
        assertThat(hello.type.getParentTypes()).isEmpty();

        assertThat(emptyInterface.fields).isEmpty();
        assertThat(emptyInterface.implementedInterfaces).isEmpty();
        assertThat(emptyInterface.type.getParentTypes()).isEmpty();
        assertThat(emptyInterface.methods).isEmpty();
        assertThat(emptyInterface.methodHandles).isEmpty();

        assertThat(funCode.methods.keySet().stream().map(it -> it.name).collect(toSet()))
                .containsExactlyInAnyOrderElementsOf(Set.of("\"<init>\"", "countLengths", "filter", "logLengthsStats",
                        "lambda$filter$1", "lambda$countLengths$0"));

        assertThat(bar.methods.keySet().stream().map(it -> it.name).collect(toSet()))
                .containsExactlyInAnyOrderElementsOf(Set.of("\"<init>\""));
    }

    @Test
    void canParseBasicGenericClass() {
        var out = new ByteArrayOutputStream();
        var parser = new JavapOutputParser(new JBuildLog(new PrintStream(out), false));
        var types = parser.processJavapOutput(javap(myClassesJar, "generics.BasicGenerics"));
        var result = types.get("Lgenerics/BasicGenerics;");

        assertThat(result.typeName).isEqualTo("Lgenerics/BasicGenerics;");
        assertThat(result.type.typeParameters).containsExactlyInAnyOrderElementsOf(Set.of(
                new JavaType.TypeParam("T", List.of(), List.of())
        ));
        assertThat(result.implementedInterfaces).isEmpty();
        assertThat(result.type.getParentTypes()).isEmpty();
        assertThat(result.fields).isEmpty();
        assertThat(result.methodHandles).isEmpty();
        assertThat(result.methods.keySet()).containsExactlyInAnyOrderElementsOf(Set.of(
                new Definition.MethodDefinition("\"<init>\"", "()V")
        ));
    }

    @Test
    void canParseGenericClass() {
        var out = new ByteArrayOutputStream();
        var parser = new JavapOutputParser(new JBuildLog(new PrintStream(out), false));
        var types = parser.processJavapOutput(javap(myClassesJar, "generics.Generics"));
        var result = types.get("Lgenerics/Generics;");

        assertThat(result.typeName).isEqualTo("Lgenerics/Generics;");
        assertThat(result.type.typeParameters).containsExactlyInAnyOrderElementsOf(Set.of(
                new JavaType.TypeParam("T", List.of(new JavaType.TypeBound("Lgenerics/Base;", List.of())), List.of())
        ));
        assertThat(result.implementedInterfaces).isEmpty();
        assertThat(result.type.getParentTypes()).isEmpty();
        assertThat(result.fields).isEmpty();
        assertThat(result.methodHandles).isEmpty();
        assertThat(result.methods.keySet()).containsExactlyInAnyOrderElementsOf(Set.of(
                new Definition.MethodDefinition("\"<init>\"", "()V"),
                new Definition.MethodDefinition("takeT", "(Lgenerics/Base;)Ljava/lang/String;"),
                // cannot see reference to generic type as it's not used and is not part of the type descriptor
                new Definition.MethodDefinition("genericMethod", "(Ljava/util/function/Function;)V")
        ));
    }

    @Test
    void canParseGenericClassWithArrayTypeBound() {
        var out = new ByteArrayOutputStream();
        var parser = new JavapOutputParser(new JBuildLog(new PrintStream(out), false));
        var types = parser.processJavapOutput(javap(myClassesJar, "generics.GenericWithArray"));
        var result = types.get("Lgenerics/GenericWithArray;");

        assertThat(result.typeName).isEqualTo("Lgenerics/GenericWithArray;");
        assertThat(result.type.typeParameters).isEmpty();
        assertThat(result.implementedInterfaces).containsExactlyInAnyOrderElementsOf(Set.of(
                "Lgenerics/GenericParameter;"
        ));
        assertThat(result.type.getParentTypes()).containsExactlyInAnyOrderElementsOf(Set.of(
                new JavaType.TypeBound("Lgenerics/GenericParameter;", List.of(
                        new JavaType.TypeParam("[Ljava/lang/Boolean;", List.of(), List.of()),
                        new JavaType.TypeParam("[[Ljava/lang/String;", List.of(), List.of())
                ))
        ));
        assertThat(result.fields).isEmpty();
        assertThat(result.methodHandles).isEmpty();
        assertThat(result.methods.keySet()).containsExactlyInAnyOrderElementsOf(Set.of(
                new Definition.MethodDefinition("\"<init>\"", "()V")
        ));
    }

    @Test
    void canParseGenericClassWithInnerGenericClass() {
        var out = new ByteArrayOutputStream();
        var parser = new JavapOutputParser(new JBuildLog(new PrintStream(out), false));
        var types = parser.processJavapOutput(javap(myClassesJar, "generics.GenericStructure"));
        var result = types.get("Lgenerics/GenericStructure;");

        assertThat(result.typeName).isEqualTo("Lgenerics/GenericStructure;");
        assertThat(result.type.typeParameters).containsExactlyInAnyOrderElementsOf(Set.of(
                new JavaType.TypeParam("D", List.of(), List.of())
        ));
        assertThat(result.implementedInterfaces).isEmpty();
        assertThat(result.type.getParentTypes()).isEmpty();
        assertThat(result.fields).isEmpty();
        assertThat(result.methodHandles).isEmpty();
        assertThat(result.methods.keySet()).containsExactlyInAnyOrderElementsOf(Set.of(
                new Definition.MethodDefinition("\"<init>\"", "()V")
        ));

        types = parser.processJavapOutput(javap(myClassesJar, "generics.GenericStructure.Data"));
        result = types.get("Lgenerics/GenericStructure$Data;");

        assertThat(result.typeName).isEqualTo("Lgenerics/GenericStructure$Data;");
        assertThat(result.type.typeParameters).containsExactlyInAnyOrderElementsOf(Set.of(
                new JavaType.TypeParam("D", List.of(), List.of())
        ));
        assertThat(result.implementedInterfaces).isEmpty();
        assertThat(result.type.getParentTypes()).isEmpty();
        assertThat(result.fields).containsExactlyInAnyOrderElementsOf(Set.of(
                new Definition.FieldDefinition("this$0", "Lgenerics/GenericStructure;"),
                new Definition.FieldDefinition("data", "Ljava/lang/Object;")
        ));
        assertThat(result.methodHandles).isEmpty();
        assertThat(result.methods.keySet()).containsExactlyInAnyOrderElementsOf(Set.of(
                new Definition.MethodDefinition("\"<init>\"", "(Lgenerics/GenericStructure;)V")
        ));

        types = parser.processJavapOutput(javap(myClassesJar, "generics.GenericStructure.OtherData"));
        result = types.get("Lgenerics/GenericStructure$OtherData;");

        assertThat(result.typeName).isEqualTo("Lgenerics/GenericStructure$OtherData;");
        assertThat(result.type.typeParameters).isEmpty();
        assertThat(result.implementedInterfaces).isEmpty();
        assertThat(result.type.getParentTypes()).containsExactlyInAnyOrderElementsOf(Set.of(
                new JavaType.TypeBound("Lgenerics/GenericStructure$Data;",
                        List.of(new JavaType.TypeParam("LD;", List.of(), List.of())))
        ));
        assertThat(result.fields).containsExactlyInAnyOrderElementsOf(Set.of(
                new Definition.FieldDefinition("this$0", "Lgenerics/GenericStructure;")
        ));
        assertThat(result.methodHandles).isEmpty();
        assertThat(result.methods.keySet()).containsExactlyInAnyOrderElementsOf(Set.of(
                new Definition.MethodDefinition("\"<init>\"", "(Lgenerics/GenericStructure;)V")
        ));
    }

    @Test
    void canParseManyGenericsClass() {
        var out = new ByteArrayOutputStream();
        var parser = new JavapOutputParser(new JBuildLog(new PrintStream(out), false));
        var types = parser.processJavapOutput(javap(myClassesJar, "generics.ManyGenerics"));
        var result = types.get("Lgenerics/ManyGenerics;");

        assertThat(result.typeName).isEqualTo("Lgenerics/ManyGenerics;");
        assertThat(result.type.typeParameters).containsExactlyInAnyOrderElementsOf(Set.of(
                new JavaType.TypeParam("A", List.of(), List.of()),
                new JavaType.TypeParam("B", List.of(), List.of()),
                new JavaType.TypeParam("C", List.of(), List.of()),
                new JavaType.TypeParam("D", List.of(), List.of())
        ));
        assertThat(result.implementedInterfaces).isEmpty();
        assertThat(result.type.getParentTypes()).isEmpty();
        assertThat(result.fields).isEmpty();
        assertThat(result.methodHandles).isEmpty();
        assertThat(result.methods.keySet()).containsExactlyInAnyOrderElementsOf(Set.of(
                new Definition.MethodDefinition("\"<init>\"", "()V")
        ));
    }

    @Test
    void canParseTypeUsingJavaMethodViaInterface() {
        var out = new ByteArrayOutputStream();
        var parser = new JavapOutputParser(new JBuildLog(new PrintStream(out), false));
        var types = parser.processJavapOutput(javap(otherClassesJar, "other.UsesMultiInterface"));
        var result = types.get("Lother/UsesMultiInterface;");

        assertThat(result.typeName).isEqualTo("Lother/UsesMultiInterface;");
        assertThat(result.type.typeParameters).isEmpty();
        assertThat(result.implementedInterfaces).isEmpty();
        assertThat(result.type.getParentTypes()).isEmpty();
        assertThat(result.fields).isEmpty();
        assertThat(result.methodHandles).isEmpty();
        assertThat(result.methods.keySet()).containsExactlyInAnyOrderElementsOf(Set.of(
                new Definition.MethodDefinition("\"<init>\"", "()V"),
                new Definition.MethodDefinition("callJavaMethodViaInterface", "(Lfoo/MultiInterface;)V")
        ));

        assertThat(result.methods.get(
                new Definition.MethodDefinition("\"<init>\"", "()V"))
        ).isEmpty();

        assertThat(result.methods.get(
                new Definition.MethodDefinition("callJavaMethodViaInterface", "(Lfoo/MultiInterface;)V")
        )).containsExactlyInAnyOrderElementsOf(Set.of(
                new Code.Method("Lfoo/MultiInterface;", "run", "()V")
        ));
    }

    @Test
    void canParseInterfaceFromRealJar() {
        var out = new ByteArrayOutputStream();
        var parser = new JavapOutputParser(new JBuildLog(new PrintStream(out), false));
        var types = parser.processJavapOutput(javap(osgiaasCliApiJar, "com.athaydes.osgiaas.cli.Cli"));
        var result = types.get("Lcom/athaydes/osgiaas/cli/Cli;");

        assertThat(result.typeName).isEqualTo("Lcom/athaydes/osgiaas/cli/Cli;");
        assertThat(result.type.typeParameters).isEmpty();
        assertThat(result.implementedInterfaces).isEmpty();
        assertThat(result.type.getParentTypes()).isEmpty();
        assertThat(result.fields).isEmpty();
        assertThat(result.methodHandles).isEmpty();
        assertThat(result.methods.keySet()).containsExactlyInAnyOrderElementsOf(Set.of(
                new Definition.MethodDefinition("start", "()V"),
                new Definition.MethodDefinition("stop", "()V"),
                new Definition.MethodDefinition("setPrompt", "(Ljava/lang/String;)V"),
                new Definition.MethodDefinition("setPromptColor", "(Lcom/athaydes/osgiaas/api/ansi/AnsiColor;)V"),
                new Definition.MethodDefinition("setErrorColor", "(Lcom/athaydes/osgiaas/api/ansi/AnsiColor;)V"),
                new Definition.MethodDefinition("setTextColor", "(Lcom/athaydes/osgiaas/api/ansi/AnsiColor;)V"),
                new Definition.MethodDefinition("clearScreen", "()V")
        ));
        assertThat(result.methods.values()).containsOnly(Set.of());
    }

    private Iterator<String> javap(File jar, String... classNames) {
        var result = Tools.Javap.create().run(jar.getPath(), classNames);
        assertProcessWasSuccessful(result);
        return result.stdout.lines().iterator();
    }

    private void assertProcessWasSuccessful(Tools.ToolRunResult result) {
        if (result.exitCode != 0) {
            throw new RuntimeException("tool failed: " + result.exitCode + ":\n" + processOutput(result));
        }
    }

    private String processOutput(Tools.ToolRunResult result) {
        return ">>> sysout:\n" + result.stdout + "\n" +
                ">>> syserr:\n" + result.stderr + "\n" +
                "---";
    }

}
