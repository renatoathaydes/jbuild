package tests;

import jbuild.artifact.Artifact;
import jbuild.java.tools.Tools;
import jbuild.maven.MavenUtils;
import org.junit.jupiter.api.Test;
import util.JBuildTestRunner;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static util.JBuildTestRunner.SystemProperties.integrationTestsRepo;

public class FetchTest extends JBuildTestRunner {

    @Test
    void canFetchPom() throws Exception {
        final var expectedFileLocation = new File("opentest4j-1.2.0.pom");
        expectedFileLocation.deleteOnExit();

        var result = runWithIntTestRepo("fetch", "org.opentest4j:opentest4j:1.2.0:pom");
        verifySuccessful("jbuild fetch", result);

        assertThat(result.getStdout()).startsWith("JBuild success in ");
        assertThat(expectedFileLocation).isFile();

        try (var stream = new FileInputStream(expectedFileLocation)) {
            var pom = MavenUtils.parsePom(stream);
            assertThat(pom.getArtifact()).isEqualTo(new Artifact("org.opentest4j", "opentest4j", "1.2.0"));
        }
    }

    @Test
    void canFetchArtifactToSpecificDir() throws Exception {
        var tempDir = Files.createTempDirectory(FetchTest.class.getSimpleName()).toFile();
        var outputDir = new File(tempDir, "outDir");
        final var expectedFileLocation = new File(outputDir, "opentest4j-1.2.0.pom");

        tempDir.deleteOnExit();
        outputDir.deleteOnExit();
        expectedFileLocation.deleteOnExit();

        var result = runWithIntTestRepo("fetch", "org.opentest4j:opentest4j:1.2.0:pom",
                "-d", outputDir.getPath());
        verifySuccessful("jbuild fetch", result);

        assertThat(result.getStdout()).startsWith("JBuild success in ");
        assertThat(expectedFileLocation).isFile();

        try (var stream = new FileInputStream(expectedFileLocation)) {
            var pom = MavenUtils.parsePom(stream);
            assertThat(pom.getArtifact()).isEqualTo(new Artifact("org.opentest4j", "opentest4j", "1.2.0"));
        }
    }

    @Test
    void canFetchJarByDefault() {
        final var expectedFileLocation = new File("opentest4j-1.2.0.jar");
        expectedFileLocation.deleteOnExit();

        var result = runWithIntTestRepo("fetch", "org.opentest4j:opentest4j:1.2.0");
        verifySuccessful("jbuild fetch", result);

        assertThat(result.getStdout()).startsWith("JBuild success in ");
        assertThat(expectedFileLocation).isFile();

        var contentsResult = Tools.Jar.create().listContents(expectedFileLocation.getPath());
        verifySuccessful("jar", contentsResult);
        assertThat(contentsResult.getStdout().lines().collect(toList())).containsExactlyElementsOf(List.of(
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "org/",
                "org/opentest4j/",
                "org/opentest4j/TestSkippedException.class",
                "org/opentest4j/IncompleteExecutionException.class",
                "org/opentest4j/AssertionFailedError.class",
                "org/opentest4j/MultipleFailuresError.class",
                "org/opentest4j/TestAbortedException.class",
                "org/opentest4j/ValueWrapper.class",
                "module-info.class"));
    }

    @Test
    void cannotFetchArtifactThatDoesNotExist() {
        var result = runWithIntTestRepo("fetch", "foo.bar:foo:1.0");
        assertThat(result.exitCode()).isEqualTo(1);

        var artifact = new Artifact("foo.bar", "foo", "1.0");
        assertThat(result.getStdout()).startsWith("Unable to retrieve " +
                artifact + " due to:" + LE +
                "  * " + artifact + " was not found in file-repository[" + integrationTestsRepo + "]" + LE +
                "An error occurred while processing foo.bar:foo:1.0" + LE +
                "ERROR: Could not fetch all artifacts successfully" + LE +
                "JBuild failed in ");
    }

}
