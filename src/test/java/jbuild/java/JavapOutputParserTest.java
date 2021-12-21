package jbuild.java;

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

        assertThat(result.className).isEqualTo("Hello");
        assertThat(result.fields).isEmpty();
        assertThat(result.methods.keySet())
                .isEqualTo(Set.of(
                        new MethodDefinition("Hello", "(Ljava/lang/String;)V"),
                        new MethodDefinition("foo", "()Z"),
                        new MethodDefinition("getMessage", "()Ljava/lang/String;")));
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
