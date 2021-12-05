package jbuild.maven;

import jbuild.artifact.Artifact;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
    void canParseMavenPomUsingProperties() throws Exception {
        MavenPom pom;
        try (var stream = getClass().getResourceAsStream("junit.pom.xml")) {
            pom = MavenUtils.parsePom(stream);
        }
        assertThat(pom)
                .has(dependencies(
                        dep("org.hamcrest", "hamcrest-core", "1.3", Scope.COMPILE),
                        dep("org.hamcrest", "hamcrest-library", "1.3", Scope.TEST)
                )).has(artifactCoordinates(new Artifact("junit", "junit", "4.13.2")));
    }

    @Test
    void canParseMavenTimestamp() {
        assertThat(MavenUtils.parseMavenTimestamp("20210927195736"))
                .isEqualTo(Instant.parse("2021-09-27T19:57:36.00Z"));
    }

    @Test
    void canParseMavenProperties() {
        class Example {
            final String value;
            final Map<String, String> properties;
            final String expectedResolvedValue;

            public Example(String value, Map<String, String> properties, String expectedResolvedValue) {
                this.value = value;
                this.properties = properties;
                this.expectedResolvedValue = expectedResolvedValue;
            }
        }

        List.of(
                new Example("foo", Map.of("foo", "no"), "foo"),
                new Example("$foo", Map.of("foo", "no"), "$foo"),
                new Example("${foo}", Map.of("foo", "yes"), "yes"),
                new Example("${bar", Map.of("foo", "no", "bar", "no"), "${bar"),
                new Example("${bar}", Map.of("foo", "no", "bar", "zort"), "zort"),
                new Example("${foo.bar}", Map.of("foo", "no", "bar", "zort", "foo.bar", "good"), "good"),
                new Example("${bar}${foo}", Map.of("foo", "no", "bar", "zort"), "${bar}${foo}")
        ).forEach(example ->
                assertThat(MavenUtils.resolveProperty(example.value, example.properties))
                        .isEqualTo(example.expectedResolvedValue)
        );
    }

    private static Dependency dep(String groupId, String artifactId, String version, Scope scope) {
        return new Dependency(new Artifact(groupId, artifactId, version), scope);
    }

    private static Dependency dep(String groupId, String artifactId, String version) {
        return new Dependency(new Artifact(groupId, artifactId, version));
    }

}
