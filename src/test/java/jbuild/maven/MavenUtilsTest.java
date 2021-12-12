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
        assertThat(readPom("javana.pom.xml"))
                .has(dependencies(dep("junit", "junit", "4.12", Scope.TEST)))
                .has(artifactCoordinates(new Artifact("com.athaydes.javanna", "javanna", "1.1")));
    }

    @Test
    void canParseChildPomWithParentPom() throws Exception {
        var pom = readPom("child.pom.xml");
        var parent = readPom("parent.pom.xml");

        assertThat(pom.withParent(parent))
                .has(dependencies(
                        dep("com.google.code.findbugs", "jsr305", "3.0.2", Scope.COMPILE),
                        dep("com.athaydes.jbuild", "jbuild", "3.2.1", Scope.COMPILE),
                        dep("com.athaydes", "jbuild-example", "1.2.3", Scope.COMPILE)))
                .has(artifactCoordinates(new Artifact("com.athaydes.test", "jbuild-child", "1.0")));
    }

    @Test
    void canParseBigMavenPom() throws Exception {
        var pom = readPom("guava.pom.xml");
        var parent = readPom("guava-parent.pom.xml");

        assertThat(pom.withParent(parent))
                .has(dependencies(
                        dep("com.google.guava", "failureaccess", "1.0.1"),
                        dep("com.google.guava", "listenablefuture", "9999.0-empty-to-avoid-conflict-with-guava"),
                        dep("com.google.code.findbugs", "jsr305", "3.0.2"),
                        dep("org.checkerframework", "checker-qual", "3.12.0"),
                        dep("com.google.errorprone", "error_prone_annotations", "2.7.1"),
                        dep("com.google.j2objc", "j2objc-annotations", "1.3")))
                .has(artifactCoordinates(new Artifact("com.google.guava", "guava", "31.0.1-jre")));
    }

    @Test
    void canParseMavenPomUsingProperties() throws Exception {
        assertThat(readPom("junit.pom.xml"))
                .has(dependencies(
                        dep("org.hamcrest", "hamcrest-core", "1.3", Scope.COMPILE),
                        dep("org.hamcrest", "hamcrest-library", "1.3", Scope.TEST)
                )).has(artifactCoordinates(new Artifact("junit", "junit", "4.13.2")));
    }

    @Test
    void canParseMavenPomUsingProjectPropertiesSyntaxInParentPom() throws Exception {
        var pom = readPom("slf4j-simple.pom.xml");
        var parent = readPom("slf4j-parent.pom.xml");

        assertThat(pom.withParent(parent))
                .has(dependencies(
                        dep("org.slf4j", "slf4j-api", "1.7.32", Scope.COMPILE),
                        dep("org.slf4j", "slf4j-api", "1.7.32", Scope.TEST),
                        dep("junit", "junit", "4.12", Scope.TEST)
                )).has(artifactCoordinates(new Artifact("org.slf4j", "slf4j-simple", "1.7.32")));
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

    private static MavenPom readPom(String resourcePath) throws Exception {
        try (var stream = MavenUtilsTest.class.getResourceAsStream(resourcePath)) {
            return MavenUtils.parsePom(stream);
        }
    }

    private static Dependency dep(String groupId, String artifactId, String version, Scope scope) {
        return new Dependency(new Artifact(groupId, artifactId, version), scope);
    }

    private static Dependency dep(String groupId, String artifactId, String version) {
        return new Dependency(new Artifact(groupId, artifactId, version));
    }

}
