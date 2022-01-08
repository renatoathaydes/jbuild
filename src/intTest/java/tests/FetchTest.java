package tests;

import jbuild.artifact.Artifact;
import jbuild.java.Tools;
import jbuild.maven.MavenUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import util.JBuildTestRunner;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class FetchTest extends JBuildTestRunner {

    private static boolean outDirExists;

    @BeforeAll
    static void beforeAll() {
        outDirExists = new File("out").isDirectory();
    }

    @AfterAll
    static void afterAll() {
        if (!outDirExists) {
            var outDir = new File("out");
            if (outDir.isDirectory()) {
                var files = outDir.listFiles();
                if (files != null) for (var file : files) {
                    if (file.isDirectory()) {
                        throw new IllegalStateException("Did not expect directory inside out/ dir");
                    }
                    assertThat(file.delete()).isTrue();
                }
            }
            assertThat(outDir.delete()).isTrue();
        }
    }

    @Test
    void canFetchPom() throws Exception {
        final var expectedFileLocation = new File("out", "opentest4j-1.2.0.pom");

        var result = runWithIntTestRepo("fetch", "org.opentest4j:opentest4j:1.2.0:pom");
        verifySuccessful("jbuild fetch", result);

        assertThat(result.stdout).startsWith("JBuild success in ");
        assertThat(expectedFileLocation).isFile();

        try (var stream = new FileInputStream(expectedFileLocation)) {
            var pom = MavenUtils.parsePom(stream);
            assertThat(pom.getArtifact()).isEqualTo(new Artifact("org.opentest4j", "opentest4j", "1.2.0"));
        }
    }

    @Test
    void canFetchArtifactToSpecificDir(@TempDir File tempDir) throws Exception {
        var outputDir = new File(tempDir, "outDir");
        final var expectedFileLocation = new File(outputDir, "opentest4j-1.2.0.pom");

        var result = runWithIntTestRepo("fetch", "org.opentest4j:opentest4j:1.2.0:pom",
                "-d", outputDir.getPath());
        verifySuccessful("jbuild fetch", result);

        assertThat(result.stdout).startsWith("JBuild success in ");
        assertThat(expectedFileLocation).isFile();

        try (var stream = new FileInputStream(expectedFileLocation)) {
            var pom = MavenUtils.parsePom(stream);
            assertThat(pom.getArtifact()).isEqualTo(new Artifact("org.opentest4j", "opentest4j", "1.2.0"));
        }
    }

    @Test
    void canFetchJarByDefault() {
        final var expectedFileLocation = new File("out", "opentest4j-1.2.0.jar");

        var result = runWithIntTestRepo("fetch", "org.opentest4j:opentest4j:1.2.0");
        verifySuccessful("jbuild fetch", result);

        assertThat(result.stdout).startsWith("JBuild success in ");
        assertThat(expectedFileLocation).isFile();

        var contentsResult = Tools.Jar.create().listContents(expectedFileLocation.getPath());
        verifySuccessful("jar", contentsResult);
        assertThat(contentsResult.stdout.lines().collect(toList())).containsExactlyElementsOf(List.of(
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

}
