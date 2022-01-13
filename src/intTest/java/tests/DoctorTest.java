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

        var warnings = result.stdout.lines()
                .filter(it -> it.startsWith("WARNING"))
                .collect(toList());
        assertThat(warnings).isEmpty();

        // TODO assert success when all errors are fixed
        assertThat(result.stdout).isEmpty();
    }

}
