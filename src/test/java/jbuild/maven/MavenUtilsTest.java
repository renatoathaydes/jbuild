package jbuild.maven;

import jbuild.artifact.Artifact;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static jbuild.maven.MavenAssertions.artifactCoordinates;
import static jbuild.maven.MavenAssertions.dependencies;
import static jbuild.maven.MavenAssertions.dependencyManagement;
import static jbuild.maven.MavenHelper.dep;
import static jbuild.maven.MavenHelper.readPom;
import static jbuild.maven.MavenUtils.importsOf;
import static jbuild.maven.Scope.TEST;
import static org.assertj.core.api.Assertions.assertThat;

public class MavenUtilsTest {

    @Test
    void canParseSimpleMavenPom() throws Exception {
        assertThat(readPom("javana.pom.xml"))
                .has(dependencies(dep("junit", "junit", "4.12", TEST)))
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
                        dep("org.hamcrest", "hamcrest-library", "1.3", TEST)
                )).has(artifactCoordinates(new Artifact("junit", "junit", "4.13.2")));
    }

    @Test
    void canParseMavenPomUsingProjectPropertiesSyntaxInParentPom() throws Exception {
        var pom = readPom("slf4j-simple.pom.xml");
        var parent = readPom("slf4j-parent.pom.xml");

        assertThat(pom.withParent(parent))
                .has(dependencies(
                        dep("org.slf4j", "slf4j-api", "1.7.32", Scope.COMPILE),
                        dep("org.slf4j", "slf4j-api", "1.7.32", TEST),
                        dep("junit", "junit", "4.12", TEST)
                )).has(artifactCoordinates(new Artifact("org.slf4j", "slf4j-simple", "1.7.32")));
    }

    @Test
    void canParsePomHierarchyUsingMavenBOM() throws Exception {
        var bom = readPom("bom/bom.pom.xml");
        var parent = readPom("bom/parent.pom.xml");
        var pom1 = readPom("bom/project1.pom.xml");
        var pom2 = readPom("bom/project2.pom.xml");

        assertThat(pom1.withParent(parent.withParent(bom)))
                .has(dependencies(
                        dep("log4j", "log4j", "1.2.12", Scope.COMPILE)
                )).has(artifactCoordinates(new Artifact("com.test", "project1", "1.0.0")));

        assertThat(pom2.withParent(parent.withParent(bom)))
                .has(dependencies(
                        dep("commons-logging", "commons-logging", "1.1.1", Scope.COMPILE)
                )).has(artifactCoordinates(new Artifact("com.test", "project2", "1.0.0")));
    }

    @Test
    void canFindImportsOfPom() throws Exception {
        var commonsLang3 = readPom("commons-lang3-3.12.0.pom");
        System.out.println("commonsLang3: " + commonsLang3);

        assertThat(importsOf(commonsLang3))
                .isEqualTo(Set.of(new Artifact("org.junit", "junit-bom", "5.7.1")));
    }

    @Test
    void canParsePomUsingMavenBOM() throws Exception {
        var bom = readPom("bom/bom.pom.xml");
        var pom = readPom("bom/third-party.pom.xml");

        assertThat(pom.importing(bom))
                .has(dependencies(
                        dep("com.test", "project1", "1.0.0", Scope.COMPILE),
                        dep("com.test", "project2", "1.0.0", TEST)
                )).has(artifactCoordinates(new Artifact("com.test", "use", "1.0.0")));
    }

    @Test
    void canHandleFullPomHierarchyOfApacheCommonsLang3() throws Exception {
        var commonsLang3 = readPom("commons-lang3-3.12.0.pom");
        var commonsParent = readPom("commons-parent-52.pom");
        var apache = readPom("apache-23.pom");
        var junitBOM = readPom("junit-bom-5.7.1.pom");

        var pom = commonsLang3
                .withParent(commonsParent.withParent(apache))
                .importing(junitBOM);

        assertThat(pom).has(dependencies(
                dep("org.junit.jupiter", "junit-jupiter", "5.7.1", TEST),
                dep("org.junit-pioneer", "junit-pioneer", "1.3.0", TEST),
                dep("org.hamcrest", "hamcrest", "2.2", TEST),
                dep("org.easymock", "easymock", "4.2", TEST),
                dep("org.openjdk.jmh", "jmh-core", "1.27", TEST),
                dep("org.openjdk.jmh", "jmh-generator-annprocess", "1.27", TEST),
                dep("com.google.code.findbugs", "jsr305", "3.0.2", TEST)
        )).has(artifactCoordinates(new Artifact("org.apache.commons", "commons-lang3", "3.12.0")));
    }

    @Test
    void canParseJunitPioneerImportingJUnitBOM() throws Exception {
        var junitPioneer = readPom("junit-pioneer-1.3.0.pom");
        var junitBOM = readPom("junit-bom-5.7.1.pom");

        var pom = junitPioneer.importing(junitBOM);

        assertThat(pom).has(dependencies(
                dep("org.junit.jupiter", "junit-jupiter-api", "5.7.1", Scope.RUNTIME),
                dep("org.junit.jupiter", "junit-jupiter-params", "5.7.1", Scope.RUNTIME),
                dep("org.junit.platform", "junit-platform-commons", "1.7.1", Scope.RUNTIME),
                dep("org.junit.platform", "junit-platform-launcher", "1.7.1", Scope.RUNTIME)
        )).has(artifactCoordinates(new Artifact("org.junit-pioneer", "junit-pioneer", "1.3.0")));
    }

    @Test
    void canParseProjectPropertiesInDependencies() throws Exception {
        assertThat(readPom("api-util-1.0.0-M20.pom"))
                .has(dependencies(
                        dep("org.apache.directory.junit", "junit-addons", "", TEST), // version is from parent
                        dep("org.apache.directory.api", "api-i18n", "")
                )).has(dependencyManagement(
                        dep("api-util", "foo.bar", "1.0.0-M20")
                )).has(artifactCoordinates(new Artifact("org.apache.directory.api", "api-util", "1.0.0-M20")));
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

}
