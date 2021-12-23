package jbuild.java;

import jbuild.java.code.Code;
import jbuild.java.code.FieldDefinition;
import jbuild.java.code.MethodDefinition;
import jbuild.log.JBuildLog;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.Set;

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
                new FieldDefinition("CONST", "Ljava/lang/String;"),
                new FieldDefinition("aFloat", "F"),
                new FieldDefinition("protectedInt", "I")
        ));

        assertThat(result.methods.keySet())
                .isEqualTo(Set.of(
                        new MethodDefinition("Hello", "(Ljava/lang/String;)V"),
                        new MethodDefinition("foo", "()Z"),
                        new MethodDefinition("theFloat", "(FJ)F"),
                        new MethodDefinition("getMessage", "()Ljava/lang/String;")));

        assertThat(result.methods.get(new MethodDefinition("Hello", "(Ljava/lang/String;)V"))).isEmpty();
        assertThat(result.methods.get(new MethodDefinition("foo", "()Z"))).isEmpty();
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
                new FieldDefinition("SOMETHING", "Lfoo/SomeEnum;"),
                new FieldDefinition("NOTHING", "Lfoo/SomeEnum;"))
        );

        assertThat(result.methods.keySet()).isEqualTo(Set.of(
                new MethodDefinition("values", "()[Lfoo/SomeEnum;"),
                new MethodDefinition("valueOf", "(Ljava/lang/String;)Lfoo/SomeEnum;"),
                new MethodDefinition("static{}", "()V")));

        assertThat(result.methods.get(new MethodDefinition("values", "()[Lfoo/SomeEnum;")))
                .isEmpty();

        assertThat(result.methods.get(new MethodDefinition("valueOf", "(Ljava/lang/String;)Lfoo/SomeEnum;")))
                .isEmpty();

        assertThat(result.methods.get(new MethodDefinition("static{}", "()V")))
                .isEmpty();
    }

    private Iterator<String> javap(String jar, String className) {
        var result = Tools.Javap.create().run(jar, className);
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
