package tests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import util.JBuildTestRunner;

import java.nio.file.Path;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class DoctorTest extends JBuildTestRunner {

    @TempDir
    Path tempDir;

    @Test
    void canCheckGuavaDependencies() {
        // install the artifact in the tempDir
        var result = runWithIntTestRepo("install", "-d", tempDir.toString(), Artifacts.GUAVA);
        verifySuccessful("jbuild install", result);

        // verify that the jar is valid
        result = runWithIntTestRepo("doctor", tempDir.toString(), "-y", "-e", Artifacts.GUAVA_JAR_NAME);

        verifySuccessful("jbuild deps", result);

        var warnings = result.getStdout().lines()
                .filter(it -> it.startsWith("WARNING"))
                .collect(toList());
        assertThat(warnings).isEmpty();

        assertThat(result.getStdout()).contains("Found a single classpath permutation, checking its consistency." + LE +
                "All entrypoint type dependencies are satisfied by the classpath below:");
        assertThat(result.getStdout()).contains(LE + "JBuild success in");
    }

    @Test
    void canCheckGroovyDependencies() {
        // install the artifact in the tempDir
        var result = runWithIntTestRepo("install", "-d", tempDir.toString(), Artifacts.GROOVY);
        verifySuccessful("jbuild install", result);

        // verify that the jar is valid
        result = runWithIntTestRepo("doctor", tempDir.toString(), "-y",
                "-e", Artifacts.GROOVY_JAR_NAME,
                "-x", "Lorg\\/apache\\/ivy\\/.*",
                "-x", "Lorg\\/stringtemplate\\/.*",
                "-x", "Lorg\\/abego\\/.*",
                "-x", "Lgroovyjarjarantlr4\\/.*",
                "-x", "Lorg\\/fusesource\\/jansi\\/.*",
                "-x", "Lversion;",
                "-x", "Lcom\\/thoughtworks\\/xstream\\/.*",
                "-x", "Lgroovyjarjarasm\\/asm\\/util\\/ASMifierSupport;");

        verifySuccessful("jbuild deps", result);

        var warnings = result.getStdout().lines()
                .filter(it -> it.startsWith("WARNING"))
                .collect(toList());
        assertThat(warnings).isEmpty();

        assertThat(result.getStdout()).contains("Found a single classpath permutation, checking its consistency." + LE +
                "All entrypoint type dependencies are satisfied by the classpath below:");
        assertThat(result.getStdout()).contains(LE + "JBuild success in");
    }

}
