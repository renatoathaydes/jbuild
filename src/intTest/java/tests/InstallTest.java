package tests;

import org.junit.jupiter.api.Test;
import util.JBuildTestRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Note: the install program is used to initialize the {@link JBuildTestRunner}, so we already know that
 * installing a few artifacts into a Maven repository works.
 */
public class InstallTest extends JBuildTestRunner {

    @Test
    void canInstallJarsIntoSingleFolder() throws IOException {
        var dir = Files.createTempDirectory(InstallTest.class.getName());
        dir.toFile().deleteOnExit();
        var result = runWithIntTestRepo("install", "-d", dir.toFile().getPath(),
                Artifacts.JUNIT5_ENGINE, Artifacts.GUAVA);

        verifySuccessful("jbuild install", result);
        assertThat(result.stdout).startsWith(
                "Will install 6 artifacts at " + dir + "\n" +
                        "Will install 7 artifacts at " + dir + "\n" +
                        "Successfully installed 13 artifacts at " + dir + "\n" +
                        "JBuild success in ");

        var jars = dir.toFile().listFiles();

        assertThat(jars).isNotNull();
        assertThat(Arrays.stream(jars)
                .map(File::getName)
                .collect(toSet()))
                .containsExactlyInAnyOrder(
                        "apiguardian-api-1.1.0.jar",
                        "failureaccess-1.0.1.jar",
                        "jsr305-3.0.2.jar",
                        "junit-platform-commons-1.7.0.jar",
                        "opentest4j-1.2.0.jar",
                        "checker-qual-3.12.0.jar",
                        "guava-31.0.1-jre.jar",
                        "junit-jupiter-api-5.7.0.jar",
                        "junit-platform-engine-1.7.0.jar",
                        "error_prone_annotations-2.7.1.jar",
                        "j2objc-annotations-1.3.jar",
                        "junit-jupiter-engine-5.7.0.jar",
                        "listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar"
                );
    }
}
