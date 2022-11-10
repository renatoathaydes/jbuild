package tests;

import jbuild.artifact.Artifact;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import util.JBuildTestRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static jbuild.util.TextUtils.LINE_END;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static util.JBuildTestRunner.SystemProperties.integrationTestsRepo;

/**
 * Note: the install program is used to initialize the {@link JBuildTestRunner}, so we already know that
 * installing a few artifacts into a Maven repository works.
 */
public class InstallTest extends JBuildTestRunner {

    @Test
    void canInstallJarsIntoLocalMavenRepo() {
        // the install command is used to setup the initial Maven repository,
        // so we just need to check the mvn-repo contents in this test

        var rootDirectories = SystemProperties.integrationTestsRepo.listFiles();

        assertThat(rootDirectories).isNotNull();

        List<String> expectedLines = ("" +
                "asm" + LE +
                "  asm" + LE +
                "    3.2" + LE +
                "      asm-3.2.jar" + LE +
                "      asm-3.2.pom" + LE +
                "  asm-parent" + LE +
                "    3.2" + LE +
                "      asm-parent-3.2.pom" + LE +
                "com" + LE +
                "  github" + LE +
                "    luben" + LE +
                "      zstd-jni" + LE +
                "        1.5.0-2" + LE +
                "          zstd-jni-1.5.0-2.jar" + LE +
                "          zstd-jni-1.5.0-2.pom" + LE +
                "  google" + LE +
                "    code" + LE +
                "      findbugs" + LE +
                "        jsr305" + LE +
                "          3.0.2" + LE +
                "            jsr305-3.0.2.jar" + LE +
                "            jsr305-3.0.2.pom" + LE +
                "    errorprone" + LE +
                "      error_prone_annotations" + LE +
                "        2.7.1" + LE +
                "          error_prone_annotations-2.7.1.jar" + LE +
                "          error_prone_annotations-2.7.1.pom" + LE +
                "      error_prone_parent" + LE +
                "        2.7.1" + LE +
                "          error_prone_parent-2.7.1.pom" + LE +
                "    guava" + LE +
                "      failureaccess" + LE +
                "        1.0.1" + LE +
                "          failureaccess-1.0.1.jar" + LE +
                "          failureaccess-1.0.1.pom" + LE +
                "      guava" + LE +
                "        31.0.1-jre" + LE +
                "          guava-31.0.1-jre.jar" + LE +
                "          guava-31.0.1-jre.pom" + LE +
                "      guava-parent" + LE +
                "        26.0-android" + LE +
                "          guava-parent-26.0-android.pom" + LE +
                "        31.0.1-jre" + LE +
                "          guava-parent-31.0.1-jre.pom" + LE +
                "      listenablefuture" + LE +
                "        9999.0-empty-to-avoid-conflict-with-guava" + LE +
                "          listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar" + LE +
                "          listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.pom" + LE +
                "    j2objc" + LE +
                "      j2objc-annotations" + LE +
                "        1.3" + LE +
                "          j2objc-annotations-1.3.jar" + LE +
                "          j2objc-annotations-1.3.pom" + LE +
                "org" + LE +
                "  apache" + LE +
                "    apache" + LE +
                "      23" + LE +
                "        apache-23.pom" + LE +
                "    commons" + LE +
                "      commons-compress" + LE +
                "        1.21" + LE +
                "          commons-compress-1.21.jar" + LE +
                "          commons-compress-1.21.pom" + LE +
                "      commons-parent" + LE +
                "        52" + LE +
                "          commons-parent-52.pom" + LE +
                "  apiguardian" + LE +
                "    apiguardian-api" + LE +
                "      1.1.0" + LE +
                "        apiguardian-api-1.1.0.jar" + LE +
                "        apiguardian-api-1.1.0.pom" + LE +
                "  brotli" + LE +
                "    dec" + LE +
                "      0.1.2" + LE +
                "        dec-0.1.2.jar" + LE +
                "        dec-0.1.2.pom" + LE +
                "    parent" + LE +
                "      0.1.2" + LE +
                "        parent-0.1.2.pom" + LE +
                "  checkerframework" + LE +
                "    checker-qual" + LE +
                "      3.12.0" + LE +
                "        checker-qual-3.12.0.jar" + LE +
                "        checker-qual-3.12.0.pom" + LE +
                "  codehaus" + LE +
                "    groovy" + LE +
                "      groovy" + LE +
                "        3.0.9" + LE +
                "          groovy-3.0.9.jar" + LE +
                "          groovy-3.0.9.pom" + LE +
                "  junit" + LE +
                "    junit-bom" + LE +
                "      5.7.0" + LE +
                "        junit-bom-5.7.0.pom" + LE +
                "    jupiter" + LE +
                "      junit-jupiter-api" + LE +
                "        5.7.0" + LE +
                "          junit-jupiter-api-5.7.0.jar" + LE +
                "          junit-jupiter-api-5.7.0.pom" + LE +
                "      junit-jupiter-engine" + LE +
                "        5.7.0" + LE +
                "          junit-jupiter-engine-5.7.0.jar" + LE +
                "          junit-jupiter-engine-5.7.0.pom" + LE +
                "    platform" + LE +
                "      junit-platform-commons" + LE +
                "        1.7.0" + LE +
                "          junit-platform-commons-1.7.0.jar" + LE +
                "          junit-platform-commons-1.7.0.pom" + LE +
                "      junit-platform-engine" + LE +
                "        1.7.0" + LE +
                "          junit-platform-engine-1.7.0.jar" + LE +
                "          junit-platform-engine-1.7.0.pom" + LE +
                "  opentest4j" + LE +
                "    opentest4j" + LE +
                "      1.2.0" + LE +
                "        opentest4j-1.2.0.jar" + LE +
                "        opentest4j-1.2.0.pom" + LE +
                "  sonatype" + LE +
                "    oss" + LE +
                "      oss-parent" + LE +
                "        7" + LE +
                "          oss-parent-7.pom" + LE +
                "        9" + LE +
                "          oss-parent-9.pom" + LE +
                "  tukaani" + LE +
                "    xz" + LE +
                "      1.9" + LE +
                "        xz-1.9.jar" + LE +
                "        xz-1.9.pom" +
                "").lines().collect(toList());

        assertThat(fileTreeString(rootDirectories).lines().collect(toList()))
                .containsExactlyElementsOf(expectedLines);
    }

    @Test
    void canInstallToBothDirAndMavenLocal() throws IOException {
        var mavenHome = System.getenv("MAVEN_HOME");
        assumeTrue(mavenHome != null,
                "Test requires MAVEN_HOME to be set");

        var tempDirectory = Files.createTempDirectory(InstallTest.class.getName());
        tempDirectory.toFile().deleteOnExit();
        var mavenRepoCopy = new File(tempDirectory.toFile(), "repo");

        var dir = Files.createTempDirectory(InstallTest.class.getName());
        dir.toFile().deleteOnExit();

        // move the Maven repo to another location, so we can simulate installing back to Maven local
        FileUtils.moveDirectory(integrationTestsRepo, mavenRepoCopy);

        try {
            var result = runWithRepo(mavenRepoCopy.toPath(), "install", "-m", "-d", dir.toString(), Artifacts.GUAVA);
            verifySuccessful("jbuild install", result);
            assertThat(result.getStdout()).startsWith("Will install 7 artifacts at [" + dir + ", " + integrationTestsRepo.getAbsolutePath() + "]" + LE +
                    "Successfully installed 7 artifacts at [" + dir + ", " + integrationTestsRepo.getAbsolutePath() + "]" + "" + LE +
                    "JBuild success in ");

            var jars = dir.toFile().listFiles();

            assertThat(jars).isNotNull();
            assertThat(Arrays.stream(jars)
                    .map(File::getName)
                    .collect(toSet()))
                    .containsExactlyInAnyOrder(
                            "j2objc-annotations-1.3.jar",
                            "error_prone_annotations-2.7.1.jar",
                            "failureaccess-1.0.1.jar",
                            "jsr305-3.0.2.jar",
                            "checker-qual-3.12.0.jar",
                            "guava-31.0.1-jre.jar",
                            "listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar"
                    );

            List<String> expectedLines = ("" +
                    "com" + LE +
                    "  google" + LE +
                    "    code" + LE +
                    "      findbugs" + LE +
                    "        jsr305" + LE +
                    "          3.0.2" + LE +
                    "            jsr305-3.0.2.jar" + LE +
                    "            jsr305-3.0.2.pom" + LE +
                    "    errorprone" + LE +
                    "      error_prone_annotations" + LE +
                    "        2.7.1" + LE +
                    "          error_prone_annotations-2.7.1.jar" + LE +
                    "          error_prone_annotations-2.7.1.pom" + LE +
                    "      error_prone_parent" + LE +
                    "        2.7.1" + LE +
                    "          error_prone_parent-2.7.1.pom" + LE +
                    "    guava" + LE +
                    "      failureaccess" + LE +
                    "        1.0.1" + LE +
                    "          failureaccess-1.0.1.jar" + LE +
                    "          failureaccess-1.0.1.pom" + LE +
                    "      guava" + LE +
                    "        31.0.1-jre" + LE +
                    "          guava-31.0.1-jre.jar" + LE +
                    "          guava-31.0.1-jre.pom" + LE +
                    "      guava-parent" + LE +
                    "        26.0-android" + LE +
                    "          guava-parent-26.0-android.pom" + LE +
                    "        31.0.1-jre" + LE +
                    "          guava-parent-31.0.1-jre.pom" + LE +
                    "      listenablefuture" + LE +
                    "        9999.0-empty-to-avoid-conflict-with-guava" + LE +
                    "          listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar" + LE +
                    "          listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.pom" + LE +
                    "    j2objc" + LE +
                    "      j2objc-annotations" + LE +
                    "        1.3" + LE +
                    "          j2objc-annotations-1.3.jar" + LE +
                    "          j2objc-annotations-1.3.pom" + LE +
                    "org" + LE +
                    "  checkerframework" + LE +
                    "    checker-qual" + LE +
                    "      3.12.0" + LE +
                    "        checker-qual-3.12.0.jar" + LE +
                    "        checker-qual-3.12.0.pom" + LE +
                    "  sonatype" + LE +
                    "    oss" + LE +
                    "      oss-parent" + LE +
                    "        7" + LE +
                    "          oss-parent-7.pom" + LE +
                    "        9" + LE +
                    "          oss-parent-9.pom" + LE +
                    "").lines().collect(toList());

            // notice that the test environment sets MAVEN_HOME to integrationTestsRepo
            var rootDirectories = SystemProperties.integrationTestsRepo.listFiles();
            assertThat(fileTreeString(rootDirectories).lines().collect(toList()))
                    .containsExactlyElementsOf(expectedLines);
        } finally {
            FileUtils.deleteDirectory(integrationTestsRepo);
            FileUtils.moveDirectory(mavenRepoCopy, integrationTestsRepo);
        }
    }

    @Test
    void canInstallToBothRepoAndMavenLocal() throws IOException {
        var mavenHome = System.getenv("MAVEN_HOME");
        assumeTrue(mavenHome != null,
                "Test requires MAVEN_HOME to be set");

        var tempDirectory = Files.createTempDirectory(InstallTest.class.getName());
        tempDirectory.toFile().deleteOnExit();
        var mavenRepoCopy = new File(tempDirectory.toFile(), "repo");

        var dir = Files.createTempDirectory(InstallTest.class.getName());
        dir.toFile().deleteOnExit();

        // move the Maven repo to another location, so we can simulate installing back to Maven local
        FileUtils.moveDirectory(integrationTestsRepo, mavenRepoCopy);

        try {
            var result = runWithRepo(mavenRepoCopy.toPath(), "install", "--maven-local",
                    "--repository", dir.toString(), Artifacts.JUNIT5_ENGINE, Artifacts.APACHE_COMMONS_COMPRESS);
            verifySuccessful("jbuild install", result);

            assertThat(result.getStdout()).contains("Will install 6 artifacts at [" +
                    dir + ", " + integrationTestsRepo.getAbsolutePath() + "]" + LE);
            assertThat(result.getStdout()).contains("Will install 1 artifact at [" +
                    dir + ", " + integrationTestsRepo.getAbsolutePath() + "]" + LE);
            assertThat(result.getStdout()).contains("Successfully installed 7 artifacts at [" +
                    dir + ", " + integrationTestsRepo.getAbsolutePath() + "]" + "" + LE);
            assertThat(result.getStdout()).contains("JBuild success in ");

            var dirRoots = dir.toFile().listFiles();
            var dirTree = fileTreeString(dirRoots).lines().collect(toList());

            // notice that the test environment sets MAVEN_HOME to integrationTestsRepo
            var rootDirectories = SystemProperties.integrationTestsRepo.listFiles();

            assertThat(fileTreeString(rootDirectories).lines().collect(toList()))
                    .containsExactlyElementsOf(dirTree);
        } finally {
            FileUtils.deleteDirectory(integrationTestsRepo);
            FileUtils.moveDirectory(mavenRepoCopy, integrationTestsRepo);
        }
    }

    @Test
    void canInstallJarsIntoSingleFolderTransitive() throws IOException {
        var dir = Files.createTempDirectory(InstallTest.class.getName());
        dir.toFile().deleteOnExit();
        var result = runWithIntTestRepo("install", "-d", dir.toFile().getPath(),
                Artifacts.JUNIT5_ENGINE, Artifacts.GUAVA);

        verifySuccessful("jbuild install", result);

        // the "Will install..." message runs async, either one can show up first
        assertThat(result.getStdout()).satisfiesAnyOf(
                stdout ->
                        assertThat(stdout).startsWith("Will install 6 artifacts at " + dir + "" + LE +
                                "Will install 7 artifacts at " + dir + "" + LE +
                                "Successfully installed 13 artifacts at " + dir + "" + LE +
                                "JBuild success in "),
                stdout ->
                        assertThat(stdout).startsWith("Will install 7 artifacts at " + dir + "" + LE +
                                "Will install 6 artifacts at " + dir + "" + LE +
                                "Successfully installed 13 artifacts at " + dir + "" + LE +
                                "JBuild success in "));

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

    @Test
    void canInstallJarsIntoSingleFolderTransitiveWithExclusions() throws IOException {
        var dir = Files.createTempDirectory(InstallTest.class.getName());
        dir.toFile().deleteOnExit();
        var result = runWithIntTestRepo("install", "-d", dir.toFile().getPath(),
                "-x", ".*jsr305.*|org\\.(api|open).*", "-x", ".*9999.*", "-x", "foobar",
                Artifacts.JUNIT5_ENGINE, Artifacts.GUAVA);

        verifySuccessful("jbuild install", result);

        // the "Will install..." message runs async, either one can show up first
        assertThat(result.getStdout()).satisfiesAnyOf(
                stdout ->
                        assertThat(stdout).startsWith("Will install 4 artifacts at " + dir + "" + LE +
                                "Will install 5 artifacts at " + dir + "" + LE +
                                "Successfully installed 9 artifacts at " + dir + "" + LE +
                                "JBuild success in "),
                stdout ->
                        assertThat(stdout).startsWith("Will install 5 artifacts at " + dir + "" + LE +
                                "Will install 4 artifacts at " + dir + "" + LE +
                                "Successfully installed 9 artifacts at " + dir + "" + LE +
                                "JBuild success in "));

        var jars = dir.toFile().listFiles();

        assertThat(jars).isNotNull();
        assertThat(Arrays.stream(jars)
                .map(File::getName)
                .collect(toSet()))
                .containsExactlyInAnyOrder(
                        "failureaccess-1.0.1.jar",
                        "junit-platform-commons-1.7.0.jar",
                        "checker-qual-3.12.0.jar",
                        "guava-31.0.1-jre.jar",
                        "junit-jupiter-api-5.7.0.jar",
                        "junit-platform-engine-1.7.0.jar",
                        "error_prone_annotations-2.7.1.jar",
                        "j2objc-annotations-1.3.jar",
                        "junit-jupiter-engine-5.7.0.jar"
                );
    }

    @Test
    void cannotFetchArtifactThatDoesNotExist() {
        var result = runWithIntTestRepo("install", "foo.bar:foo:1.0");
        assertThat(result.exitCode()).isEqualTo(6);

        var artifact = new Artifact("foo.bar", "foo", "1.0", "pom");
        assertThat(result.getStdout()).startsWith("Unable to retrieve " +
                artifact + " due to:" + LE +
                "  * " + artifact + " was not found in file-repository[" + integrationTestsRepo + "]" + LE +
                "ERROR: Could not install all artifacts successfully" + LE +
                "JBuild failed in ");
    }

    private static String fileTreeString(File[] files) {
        var builder = new StringBuilder();
        fileTreeString(files, builder, "");
        return builder.toString();
    }

    private static void fileTreeString(File[] files, StringBuilder builder, String indentation) {
        Arrays.sort(files);

        for (var file : files) {
            builder.append(indentation).append(file.getName()).append(LINE_END);
            if (file.isDirectory()) {
                fileTreeString(file.listFiles(), builder, indentation + "  ");
            }
        }
    }
}
