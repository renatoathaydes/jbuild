package tests;

import org.junit.jupiter.api.Test;
import util.JBuildTestRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

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
                "asm\n" +
                "  asm\n" +
                "    3.2\n" +
                "      asm-3.2.jar\n" +
                "      asm-3.2.pom\n" +
                "  asm-parent\n" +
                "    3.2\n" +
                "      asm-parent-3.2.pom\n" +
                "com\n" +
                "  github\n" +
                "    luben\n" +
                "      zstd-jni\n" +
                "        1.5.0-2\n" +
                "          zstd-jni-1.5.0-2.jar\n" +
                "          zstd-jni-1.5.0-2.pom\n" +
                "  google\n" +
                "    code\n" +
                "      findbugs\n" +
                "        jsr305\n" +
                "          3.0.2\n" +
                "            jsr305-3.0.2.jar\n" +
                "            jsr305-3.0.2.pom\n" +
                "    errorprone\n" +
                "      error_prone_annotations\n" +
                "        2.7.1\n" +
                "          error_prone_annotations-2.7.1.jar\n" +
                "          error_prone_annotations-2.7.1.pom\n" +
                "      error_prone_parent\n" +
                "        2.7.1\n" +
                "          error_prone_parent-2.7.1.pom\n" +
                "    guava\n" +
                "      failureaccess\n" +
                "        1.0.1\n" +
                "          failureaccess-1.0.1.jar\n" +
                "          failureaccess-1.0.1.pom\n" +
                "      guava\n" +
                "        31.0.1-jre\n" +
                "          guava-31.0.1-jre.jar\n" +
                "          guava-31.0.1-jre.pom\n" +
                "      guava-parent\n" +
                "        26.0-android\n" +
                "          guava-parent-26.0-android.pom\n" +
                "        31.0.1-jre\n" +
                "          guava-parent-31.0.1-jre.pom\n" +
                "      listenablefuture\n" +
                "        9999.0-empty-to-avoid-conflict-with-guava\n" +
                "          listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar\n" +
                "          listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.pom\n" +
                "    j2objc\n" +
                "      j2objc-annotations\n" +
                "        1.3\n" +
                "          j2objc-annotations-1.3.jar\n" +
                "          j2objc-annotations-1.3.pom\n" +
                "org\n" +
                "  apache\n" +
                "    apache\n" +
                "      23\n" +
                "        apache-23.pom\n" +
                "    commons\n" +
                "      commons-compress\n" +
                "        1.21\n" +
                "          commons-compress-1.21.jar\n" +
                "          commons-compress-1.21.pom\n" +
                "      commons-parent\n" +
                "        52\n" +
                "          commons-parent-52.pom\n" +
                "  apiguardian\n" +
                "    apiguardian-api\n" +
                "      1.1.0\n" +
                "        apiguardian-api-1.1.0.jar\n" +
                "        apiguardian-api-1.1.0.pom\n" +
                "  brotli\n" +
                "    dec\n" +
                "      0.1.2\n" +
                "        dec-0.1.2.jar\n" +
                "        dec-0.1.2.pom\n" +
                "    parent\n" +
                "      0.1.2\n" +
                "        parent-0.1.2.pom\n" +
                "  checkerframework\n" +
                "    checker-qual\n" +
                "      3.12.0\n" +
                "        checker-qual-3.12.0.jar\n" +
                "        checker-qual-3.12.0.pom\n" +
                "  junit\n" +
                "    junit-bom\n" +
                "      5.7.0\n" +
                "        junit-bom-5.7.0.pom\n" +
                "    jupiter\n" +
                "      junit-jupiter-api\n" +
                "        5.7.0\n" +
                "          junit-jupiter-api-5.7.0.jar\n" +
                "          junit-jupiter-api-5.7.0.pom\n" +
                "      junit-jupiter-engine\n" +
                "        5.7.0\n" +
                "          junit-jupiter-engine-5.7.0.jar\n" +
                "          junit-jupiter-engine-5.7.0.pom\n" +
                "    platform\n" +
                "      junit-platform-commons\n" +
                "        1.7.0\n" +
                "          junit-platform-commons-1.7.0.jar\n" +
                "          junit-platform-commons-1.7.0.pom\n" +
                "      junit-platform-engine\n" +
                "        1.7.0\n" +
                "          junit-platform-engine-1.7.0.jar\n" +
                "          junit-platform-engine-1.7.0.pom\n" +
                "  opentest4j\n" +
                "    opentest4j\n" +
                "      1.2.0\n" +
                "        opentest4j-1.2.0.jar\n" +
                "        opentest4j-1.2.0.pom\n" +
                "  sonatype\n" +
                "    oss\n" +
                "      oss-parent\n" +
                "        7\n" +
                "          oss-parent-7.pom\n" +
                "        9\n" +
                "          oss-parent-9.pom\n" +
                "  tukaani\n" +
                "    xz\n" +
                "      1.9\n" +
                "        xz-1.9.jar\n" +
                "        xz-1.9.pom" +
                "").lines().collect(toList());

        assertThat(fileTreeString(rootDirectories).lines().collect(toList()))
                .containsExactlyElementsOf(expectedLines);
    }

    @Test
    void canInstallJarsIntoSingleFolder() throws IOException {
        var dir = Files.createTempDirectory(InstallTest.class.getName());
        dir.toFile().deleteOnExit();
        var result = runWithIntTestRepo("install", "-d", dir.toFile().getPath(),
                Artifacts.JUNIT5_ENGINE, Artifacts.GUAVA);

        verifySuccessful("jbuild install", result);

        // the "Will install..." message runs async, either one can show up first
        assertThat(result.stdout).satisfiesAnyOf(
                stdout ->
                        assertThat(stdout).startsWith("Will install 6 artifacts at " + dir + "\n" +
                                "Will install 7 artifacts at " + dir + "\n" +
                                "Successfully installed 13 artifacts at " + dir + "\n" +
                                "JBuild success in "),
                stdout ->
                        assertThat(stdout).startsWith("Will install 7 artifacts at " + dir + "\n" +
                                "Will install 6 artifacts at " + dir + "\n" +
                                "Successfully installed 13 artifacts at " + dir + "\n" +
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

    private static String fileTreeString(File[] files) {
        var builder = new StringBuilder();
        fileTreeString(files, builder, "");
        return builder.toString();
    }

    private static void fileTreeString(File[] files, StringBuilder builder, String indentation) {
        Arrays.sort(files);

        for (var file : files) {
            builder.append(indentation).append(file.getName()).append('\n');
            if (file.isDirectory()) {
                fileTreeString(file.listFiles(), builder, indentation + "  ");
            }
        }
    }
}
