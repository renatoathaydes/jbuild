package jbuild.java;

import jbuild.java.code.Code;
import jbuild.java.code.MethodDefinition;
import jbuild.log.JBuildLog;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.List;
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

        assertThat(result.className).isEqualTo("Hello");

        assertThat(result.fields.keySet()).isEqualTo(Set.of("isOk", "CONST"));
        assertThat(result.fields.get("isOk")).isEqualTo(new Code.Field("isOk", "Z"));
        assertThat(result.fields.get("CONST")).isEqualTo(new Code.Field("CONST", "Ljava/lang/String;"));

        assertThat(result.methods.keySet())
                .isEqualTo(Set.of(
                        new MethodDefinition("Hello", "(Ljava/lang/String;)V"),
                        new MethodDefinition("foo", "()Z"),
                        new MethodDefinition("getMessage", "()Ljava/lang/String;")));

        assertThat(result.methods.get(new MethodDefinition("Hello", "(Ljava/lang/String;)V")))
                .isEqualTo(List.of(
                        new Code.Method("java/lang/Object", "\"<init>\"", "()V"),
                        new Code.Field("isOk", "Z"),
                        new Code.Field("message", "Ljava/lang/String;")));

        assertThat(result.methods.get(new MethodDefinition("foo", "()Z")))
                .isEqualTo(List.of());

        assertThat(result.methods.get(new MethodDefinition("getMessage", "()Ljava/lang/String;")))
                .isEqualTo(List.of(new Code.Field("message", "Ljava/lang/String;")));
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
