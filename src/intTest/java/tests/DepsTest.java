package tests;

import jbuild.artifact.Artifact;
import org.junit.jupiter.api.Test;
import util.JBuildTestRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static util.JBuildTestRunner.SystemProperties.integrationTestsRepo;

public class DepsTest extends JBuildTestRunner {

    @Test
    void canListGuavaDependencies() {
        var result = runWithIntTestRepo("deps", Artifacts.GUAVA);

        verifySuccessful("jbuild deps", result);
        assertThat(result.stdout).startsWith("Dependencies of " + Artifacts.GUAVA + ":\n" +
                "  - scope compile\n" +
                "    * com.google.code.findbugs:jsr305:3.0.2 [compile]\n" +
                "    * com.google.errorprone:error_prone_annotations:2.7.1 [compile]\n" +
                "    * com.google.guava:failureaccess:1.0.1 [compile]\n" +
                "    * com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava [compile]\n" +
                "    * com.google.j2objc:j2objc-annotations:1.3 [compile]\n" +
                "    * org.checkerframework:checker-qual:3.12.0 [compile]\n" +
                "  6 compile dependencies listed\n" +
                "JBuild success in ");
    }

    @Test
    void canListApacheCommonsCompressDependenciesIncludingOptionalsAndSingleScope() {
        var result = runWithIntTestRepo("deps", Artifacts.APACHE_COMMONS_COMPRESS, "-O", "-s", "compile");

        verifySuccessful("jbuild deps", result);
        assertThat(result.stdout).startsWith("Dependencies of " + Artifacts.APACHE_COMMONS_COMPRESS + " (incl. optionals):\n" +
                "  - scope compile\n" +
                "    * asm:asm:3.2 [compile][optional]\n" +
                "    * com.github.luben:zstd-jni:1.5.0-2 [compile][optional]\n" +
                "    * org.brotli:dec:0.1.2 [compile][optional]\n" +
                "    * org.tukaani:xz:1.9 [compile][optional]\n" +
                "  4 compile dependencies listed\n" +
                "JBuild success in ");
    }

    @Test
    void canListApacheCommonsCompressTransitiveDependencies() {
        var result = runWithIntTestRepo("deps", "-t", Artifacts.JUNIT5_ENGINE);

        verifySuccessful("jbuild deps", result);
        assertThat(result.stdout).startsWith("Dependencies of " + Artifacts.JUNIT5_ENGINE + " (incl. transitive):\n" +
                "  - scope compile\n" +
                "    * org.apiguardian:apiguardian-api:1.1.0 [compile]\n" +
                "    * org.junit.jupiter:junit-jupiter-api:5.7.0 [compile]\n" +
                "        * org.apiguardian:apiguardian-api:1.1.0 [compile] (-)\n" +
                "        * org.junit.platform:junit-platform-commons:1.7.0 [compile]\n" +
                "            * org.apiguardian:apiguardian-api:1.1.0 [compile] (-)\n" +
                "        * org.opentest4j:opentest4j:1.2.0 [compile]\n" +
                "    * org.junit.platform:junit-platform-engine:1.7.0 [compile]\n" +
                "        * org.apiguardian:apiguardian-api:1.1.0 [compile] (-)\n" +
                "        * org.junit.platform:junit-platform-commons:1.7.0 [compile] (-)\n" +
                "        * org.opentest4j:opentest4j:1.2.0 [compile] (-)\n" +
                "  5 compile dependencies listed\n" +
                "JBuild success in ");
    }

    @Test
    void canNotGetDepsOfNonExistingArtifact() {
        var result = runWithIntTestRepo("deps", "bad:artifact:0");
        assertThat(result.exitCode).isEqualTo(1);

        var artifact = new Artifact("bad", "artifact", "0", "pom");
        assertThat(result.stdout).startsWith("Unable to retrieve " +
                artifact + " due to:\n" +
                "  * " + artifact + " was not found in file-repository[" + integrationTestsRepo + "]\n" +
                "Failed to handle bad:artifact:0\n" +
                "ERROR: Could not fetch all Maven dependencies successfully\n" +
                "JBuild failed in ");
    }

}
