package tests;

import org.junit.jupiter.api.Test;
import util.JBuildTestRunner;

import java.io.IOException;
import java.nio.file.Files;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class DoctorTest extends JBuildTestRunner {

    @Test
    void canCheckGuavaDependencies() throws IOException {
        var tempDir = Files.createTempDirectory(DoctorTest.class.getName());

        // install the artifact in the tempDir
        var result = runWithIntTestRepo("install", "-d", tempDir.toString(), Artifacts.GUAVA);
        verifySuccessful("jbuild install", result);

        // verify all installed jars are present as this test has been failing due to missing classes
        var installedFiles = tempDir.toFile().list();
        assertThat(installedFiles).isNotNull();
        assertThat(installedFiles).containsExactlyInAnyOrder(
                "checker-qual-3.12.0.jar", "guava-31.0.1-jre.jar",
                "listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar",
                "error_prone_annotations-2.7.1.jar", "j2objc-annotations-1.3.jar",
                "failureaccess-1.0.1.jar", "jsr305-3.0.2.jar");

        // verify that the jar is valid
        result = runWithIntTestRepo("doctor", tempDir.toString(), "-e", Artifacts.GUAVA_JAR_NAME);

        verifySuccessful("jbuild deps", result);

        var warnings = result.getStdout().lines()
                .filter(it -> it.startsWith("WARNING"))
                .collect(toList());
        assertThat(warnings).isEmpty();

        assertThat(result.getStdout()).contains(
                "All entrypoint type dependencies are satisfied by the classpath below:");
        assertThat(result.getStdout()).contains(LE + "JBuild success in");
    }

    @Test
    void canCheckGroovyDependencies() throws IOException {
        var tempDir = Files.createTempDirectory(DoctorTest.class.getName());

        // install the artifact in the tempDir
        var result = runWithIntTestRepo("install", "-d", tempDir.toString(), Artifacts.GROOVY);
        verifySuccessful("jbuild install", result);

        // verify that the jar is valid
        result = runWithIntTestRepo("doctor", tempDir.toString(),
                "-e", Artifacts.GROOVY_JAR_NAME,
                // exclude all the optional dependencies
                "-x", "org\\.apache\\.ivy\\..*",
                "-x", "org\\.stringtemplate\\..*",
                "-x", "org\\.abego\\..*",
                "-x", "groovyjarjarantlr4\\..*",
                "-x", "org\\.fusesource\\.jansi\\..*",
                "-x", "version",
                "-x", "com\\.ibm\\.icu\\..*",
                "-x", "com\\.thoughtworks\\.xstream\\..*",
                "-x", "groovyjarjarasm\\.asm\\.util\\.ASMifierSupport");

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
