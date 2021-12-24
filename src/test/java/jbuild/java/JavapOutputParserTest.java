package jbuild.java;

import jbuild.java.code.Code;
import jbuild.java.code.FieldDefinition;
import jbuild.java.code.MethodDefinition;
import jbuild.log.JBuildLog;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

public class JavapOutputParserTest {

    private static final String myClassesJar = System.getProperty("tests.my-test-classes.jar");

    @Test
    void canParseBasicClass() {
        var out = new ByteArrayOutputStream();
        var parser = new JavapOutputParser(new JBuildLog(new PrintStream(out), false));
        var result = parser.processJavapOutput("Hello",
                javap(myClassesJar, "Hello"));

        assertThat(result.className).isEqualTo("LHello;");

        assertThat(result.fields).isEqualTo(Set.of(
                new FieldDefinition("isOk", "Z"),
                new FieldDefinition("message", "Ljava/lang/String;"),
                new FieldDefinition("CONST", "Ljava/lang/String;"),
                new FieldDefinition("aFloat", "F"),
                new FieldDefinition("protectedInt", "I")
        ));

        assertThat(result.methodHandles).isEmpty();

        assertThat(result.methods.keySet())
                .isEqualTo(Set.of(
                        new MethodDefinition("Hello", "(Ljava/lang/String;)V"),
                        new MethodDefinition("foo", "()Z"),
                        new MethodDefinition("aPrivateMethod", "()V"),
                        new MethodDefinition("theFloat", "(FJ)F"),
                        new MethodDefinition("getMessage", "()Ljava/lang/String;")));

        assertThat(result.methods.get(new MethodDefinition("Hello", "(Ljava/lang/String;)V"))).isEmpty();
        assertThat(result.methods.get(new MethodDefinition("foo", "()Z"))).isEmpty();
        assertThat(result.methods.get(new MethodDefinition("aPrivateMethod", "()V"))).isEmpty();
        assertThat(result.methods.get(new MethodDefinition("theFloat", "(FJ)F"))).isEmpty();
        assertThat(result.methods.get(new MethodDefinition("getMessage", "()Ljava/lang/String;"))).isEmpty();
    }

    @Test
    void canParseClassWithStaticBlockAndStaticMethods() {
        var out = new ByteArrayOutputStream();
        var parser = new JavapOutputParser(new JBuildLog(new PrintStream(out), false));
        var result = parser.processJavapOutput("foo.Bar",
                javap(myClassesJar, "foo.Bar"));

        assertThat(result.className).isEqualTo("Lfoo/Bar;");

        assertThat(result.fields).isEmpty();
        assertThat(result.methodHandles).isEmpty();
        assertThat(result.methods.keySet()).isEqualTo(Set.of(new MethodDefinition("Lfoo/Bar;", "()V")));
        assertThat(result.methods.get(new MethodDefinition("Lfoo/Bar;", "()V"))).isEmpty();

        result = parser.processJavapOutput("foo.Zort",
                javap(myClassesJar, "foo.Zort"));

        assertThat(result.className).isEqualTo("Lfoo/Zort;");

        assertThat(result.fields).isEqualTo(Set.of(new FieldDefinition("bar", "Lfoo/Bar;")));

        assertThat(result.methods.keySet()).isEqualTo(Set.of(
                new MethodDefinition("static{}", "()V"),
                new MethodDefinition("getBar", "(Lfoo/Bar;)Lfoo/Bar;"),
                new MethodDefinition("createBar", "()Lfoo/Bar;"),
                new MethodDefinition("Lfoo/Zort;", "()V")
        ));
        assertThat(result.methods.get(new MethodDefinition("static{}", "()V")))
                .isEqualTo(Set.of(
                        new Code.ClassRef("Lfoo/Bar;"),
                        new Code.Method("Lfoo/Bar;", "\"<init>\"", "()V")
                ));
    }

    @Test
    void canParseBasicEnum() {
        var out = new ByteArrayOutputStream();
        var parser = new JavapOutputParser(new JBuildLog(new PrintStream(out), false));
        var result = parser.processJavapOutput("foo.SomeEnum",
                javap(myClassesJar, "foo.SomeEnum"));

        assertThat(result.className).isEqualTo("Lfoo/SomeEnum;");

        assertThat(result.fields).isEqualTo(Set.of(
                new FieldDefinition("$VALUES", "[Lfoo/SomeEnum;"),
                new FieldDefinition("SOMETHING", "Lfoo/SomeEnum;"),
                new FieldDefinition("NOTHING", "Lfoo/SomeEnum;"))
        );

        assertThat(result.methodHandles).isEmpty();

        assertThat(result.methods.keySet()).isEqualTo(Set.of(
                new MethodDefinition("Lfoo/SomeEnum;", "(Ljava/lang/String;I)V"),
                new MethodDefinition("values", "()[Lfoo/SomeEnum;"),
                new MethodDefinition("valueOf", "(Ljava/lang/String;)Lfoo/SomeEnum;"),
                new MethodDefinition("static{}", "()V")));

        assertThat(result.methods.get(new MethodDefinition("Lfoo/SomeEnum;", "(Ljava/lang/String;I)V")))
                .isEmpty();

        assertThat(result.methods.get(new MethodDefinition("values", "()[Lfoo/SomeEnum;")))
                .isEmpty();

        assertThat(result.methods.get(new MethodDefinition("valueOf", "(Ljava/lang/String;)Lfoo/SomeEnum;")))
                .isEmpty();

        assertThat(result.methods.get(new MethodDefinition("static{}", "()V")))
                .isEmpty();
    }

    @Test
    void canParseFunctionalCode() {
        var out = new ByteArrayOutputStream();
        var parser = new JavapOutputParser(new JBuildLog(new PrintStream(out), false));
        var result = parser.processJavapOutput("foo.FunctionalCode",
                javap(myClassesJar, "foo.FunctionalCode"));

        assertThat(result.className).isEqualTo("Lfoo/FunctionalCode;");
        assertThat(result.fields).isEqualTo(Set.of(new FieldDefinition("log", "Lfoo/ExampleLogger;")));

        assertThat(result.methodHandles).isEqualTo(Set.of(
                new Code.Method("Lfoo/ExampleLogger;", "debug", "(Ljava/lang/String;)V")
        ));

        assertThat(result.methods.keySet()).isEqualTo(Set.of(
                new MethodDefinition("lambda$countLengths$0", "(Lfoo/Zort;)Ljava/lang/String;"),
                new MethodDefinition("lambda$filter$1", "(Lfoo/SomeEnum;)Z"),
                new MethodDefinition("filter", "(Ljava/util/List;)Ljava/util/List;"),
                new MethodDefinition("logLengthsStats", "(Ljava/util/List;)V"),
                new MethodDefinition("countLengths", "(Ljava/util/List;)Ljava/util/IntSummaryStatistics;"),
                new MethodDefinition("Lfoo/FunctionalCode;", "(Lfoo/ExampleLogger;)V")));

        assertThat(result.methods.get(new MethodDefinition("lambda$countLengths$0", "(Lfoo/Zort;)Ljava/lang/String;")))
                .isEqualTo(Set.of(
                        new Code.Field("Lfoo/Zort;", "bar", "Lfoo/Bar;"),
                        new Code.Method("Lfoo/Zort;", "createBar", "()Lfoo/Bar;")
                ));

        assertThat(result.methods.get(new MethodDefinition("lambda$filter$1", "(Lfoo/SomeEnum;)Z")))
                .isEqualTo(Set.of(
                        new Code.Field("Lfoo/SomeEnum;", "SOMETHING", "Lfoo/SomeEnum;"),
                        new Code.Method("Lfoo/ExampleLogger;", "info", "(Ljava/lang/String;)V")
                ));

        assertThat(result.methods.get(new MethodDefinition("filter", "(Ljava/util/List;)Ljava/util/List;")))
                .isEmpty();

        assertThat(result.methods.get(new MethodDefinition("logLengthsStats", "(Ljava/util/List;)V")))
                .isEqualTo(Set.of(
                        new Code.Method("Lfoo/ExampleLogger;", "info", "(Ljava/lang/String;)V")
                ));

        assertThat(result.methods.get(new MethodDefinition("countLengths", "(Ljava/util/List;)Ljava/util/IntSummaryStatistics;")))
                .isEmpty();

        assertThat(result.methods.get(new MethodDefinition("Lfoo/FunctionalCode;", "(Lfoo/ExampleLogger;)V")))
                .isEmpty();

    }

    @Test
    void canParseMultipleClassesAtOnce() {
        var out = new ByteArrayOutputStream();
        var parser = new JavapOutputParser(new JBuildLog(new PrintStream(out), false));
        var result = parser.processJavapOutput(
                javap(myClassesJar, "Hello", "foo.FunctionalCode", "foo.Bar"));

        assertThat(result.stream().map(c -> c.className).collect(toList()))
                .isEqualTo(List.of("LHello;", "Lfoo/FunctionalCode;", "Lfoo/Bar;"));

        var hello = result.stream().filter(it -> it.className.equals("LHello;"))
                .findFirst().orElseThrow();
        var funCode = result.stream().filter(it -> it.className.equals("Lfoo/FunctionalCode;"))
                .findFirst().orElseThrow();
        var bar = result.stream().filter(it -> it.className.equals("Lfoo/Bar;"))
                .findFirst().orElseThrow();

        // make sure contents of each class didn't mix up
        assertThat(hello.methods.keySet().stream().map(it -> it.name).collect(toSet()))
                .isEqualTo(Set.of("Hello", "getMessage", "foo", "theFloat", "aPrivateMethod"));
        assertThat(funCode.methods.keySet().stream().map(it -> it.name).collect(toSet()))
                .isEqualTo(Set.of("Lfoo/FunctionalCode;", "countLengths", "filter", "logLengthsStats",
                        "lambda$filter$1", "lambda$countLengths$0"));
        assertThat(bar.methods.keySet().stream().map(it -> it.name).collect(toSet()))
                .isEqualTo(Set.of("Lfoo/Bar;"));
    }

    private Iterator<String> javap(String jar, String... classNames) {
        var result = Tools.Javap.create().run(jar, classNames);
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
