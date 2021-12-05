package jbuild.maven;

import jbuild.artifact.Artifact;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static jbuild.maven.MavenAssertions.artifactCoordinates;
import static jbuild.maven.MavenAssertions.dependencies;
import static org.assertj.core.api.Assertions.assertThat;

public class MavenUtilsTest {

    @Test
    void canParseSimpleMavenPom() throws Exception {
        MavenPom pom;
        try (var stream = getClass().getResourceAsStream("javana.pom.xml")) {
            pom = MavenUtils.parsePom(stream);
        }
        assertThat(pom)
                .has(dependencies(dep("junit", "junit", "4.12", Scope.TEST)))
                .has(artifactCoordinates(new Artifact("com.athaydes.javanna", "javanna", "1.1")));
    }

    @Test
    void canParseBigMavenPom() throws Exception {
        MavenPom pom;
        try (var stream = getClass().getResourceAsStream("guava.pom.xml")) {
            pom = MavenUtils.parsePom(stream);
        }
        assertThat(pom)
                .has(dependencies(
                        dep("com.google.guava", "failureaccess", "1.0.1"),
                        dep("com.google.guava", "listenablefuture", "9999.0-empty-to-avoid-conflict-with-guava"),
                        dep("com.google.code.findbugs", "jsr305", ""),
                        dep("org.checkerframework", "checker-qual", ""),
                        dep("com.google.errorprone", "error_prone_annotations", ""),
                        dep("com.google.j2objc", "j2objc-annotations", "")))
                .has(artifactCoordinates(new Artifact("com.google.guava", "guava", "31.0.1-jre")));
    }

    @Test
    void canParseMavenTimestamp() {
        assertThat(MavenUtils.parseMavenTimestamp("20210927195736"))
                .isEqualTo(Instant.parse("2021-09-27T19:57:36.00Z"));
    }

    private static Dependency dep(String groupId, String artifactId, String version, Scope scope) {
        return new Dependency(new Artifact(groupId, artifactId, version), scope);
    }

    private static Dependency dep(String groupId, String artifactId, String version) {
        return new Dependency(new Artifact(groupId, artifactId, version));
    }

}
