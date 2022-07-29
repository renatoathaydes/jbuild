package jbuild.maven;

import jbuild.artifact.Artifact;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static jbuild.maven.DependencyType.TEST_JAR;
import static jbuild.maven.MavenAssertions.artifactCoordinates;
import static jbuild.maven.MavenAssertions.dependencies;
import static jbuild.maven.MavenAssertions.dependencyManagement;
import static jbuild.maven.MavenAssertions.licenses;
import static jbuild.maven.MavenHelper.dep;
import static jbuild.maven.MavenHelper.readPom;
import static jbuild.maven.MavenUtils.importsOf;
import static jbuild.maven.Scope.COMPILE;
import static jbuild.maven.Scope.TEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MavenUtilsTest {

    @Test
    void canApplyDependencyExclusions() {
        assertThat(MavenUtils.applyExclusions(
                Set.of(dep("a", "b", "1"),
                        dep("a", "c", "1")),
                Set.of())
        ).isEqualTo(Set.of(dep("a", "b", "1"),
                dep("a", "c", "1")));

        assertThat(MavenUtils.applyExclusions(
                Set.of(dep("a", "b", "1"),
                        dep("a", "c", "1")),
                Set.of(ArtifactKey.of("a", "b")))
        ).isEqualTo(Set.of(dep("a", "c", "1")));

        assertThat(MavenUtils.applyExclusions(
                Set.of(dep("a", "b", "1"),
                        dep("a", "c", "1")),
                Set.of(ArtifactKey.of("a", "c")))
        ).isEqualTo(Set.of(dep("a", "b", "1")));

        assertThat(MavenUtils.applyExclusions(
                Set.of(dep("a", "b", "1"),
                        dep("a", "c", "1")),
                Set.of(ArtifactKey.of("a", "b"), ArtifactKey.of("a", "c")))
        ).isEmpty();

        assertThat(MavenUtils.applyExclusions(
                Set.of(dep("a", "b", "1"),
                        dep("a", "c", "1")),
                Set.of(ArtifactKey.of("*", "*")))
        ).isEmpty();

        assertThat(MavenUtils.applyExclusions(
                Set.of(dep("a", "b", "1"),
                        dep("a", "c", "1")),
                Set.of(ArtifactKey.of("*", "b")))
        ).isEqualTo(Set.of(dep("a", "c", "1")));

        assertThat(MavenUtils.applyExclusions(
                Set.of(dep("F", "b", "1"),
                        dep("a", "c", "1")),
                Set.of(ArtifactKey.of("a", "*")))
        ).isEqualTo(Set.of(dep("F", "b", "1")));
    }

    @Test
    void canParseSimpleMavenPom() throws Exception {
        assertThat(readPom("javana.pom.xml"))
                .has(dependencies(dep("junit", "junit", "4.12", TEST)))
                .has(artifactCoordinates(new Artifact("com.athaydes.javanna", "javanna", "1.1")));
    }

    @Test
    void canParseMavenPomWithExclusions() throws Exception {
        assertThat(readPom("with-exclusions.pom.xml"))
                .has(dependencies(
                        dep("com.athaydes.jbuild", "jbuild", "3.2.1"),
                        dep("group", "artifact", "1.0", COMPILE, false,
                                Set.of(ArtifactKey.of("org.example", "bad")))))
                .has(artifactCoordinates(new Artifact("com.jbuild", "with-exclusions", "1.1.1")));
    }

    @Test
    void canParseChildPomWithParentPom() throws Exception {
        var pom = readPom("child.pom.xml");
        var parent = readPom("parent.pom.xml");

        assertThat(pom.withParent(parent))
                .has(dependencies(
                        dep("com.google.code.findbugs", "jsr305", "3.0.2", COMPILE),
                        dep("com.athaydes.jbuild", "jbuild", "3.2.1", COMPILE),
                        dep("com.athaydes", "jbuild-example", "1.2.3", COMPILE)))
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
                        dep("org.hamcrest", "hamcrest-core", "1.3", COMPILE),
                        dep("org.hamcrest", "hamcrest-library", "1.3", TEST)
                )).has(artifactCoordinates(new Artifact("junit", "junit", "4.13.2")));
    }

    @Test
    void canParseMavenPomUsingProjectPropertiesSyntaxInParentPom() throws Exception {
        var pom = readPom("slf4j-simple.pom.xml");
        var parent = readPom("slf4j-parent.pom.xml");

        assertThat(pom.withParent(parent))
                .has(dependencies(
                        dep("org.slf4j", "slf4j-api", "1.7.32", COMPILE),
                        dep("org.slf4j", "slf4j-api", "1.7.32", TEST, TEST_JAR.string()),
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
                        dep("log4j", "log4j", "1.2.12", COMPILE)
                )).has(artifactCoordinates(new Artifact("com.test", "project1", "1.0.0")));

        assertThat(pom2.withParent(parent.withParent(bom)))
                .has(dependencies(
                        dep("commons-logging", "commons-logging", "1.1.1", COMPILE)
                )).has(artifactCoordinates(new Artifact("com.test", "project2", "1.0.0")));
    }

    @Test
    void canFindImportsOfPom() throws Exception {
        var commonsLang3 = readPom("commons-lang3-3.12.0.pom");

        assertThat(importsOf(commonsLang3))
                .isEqualTo(Set.of(new Artifact("org.junit", "junit-bom", "5.7.1")));
    }

    @Test
    void canParsePomUsingMavenBOM() throws Exception {
        var bom = readPom("bom/bom.pom.xml");
        var pom = readPom("bom/third-party.pom.xml");

        assertThat(pom.importing(bom))
                .has(dependencies(
                        dep("com.test", "project1", "1.0.0", COMPILE),
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
    void mavenPropertiesCanBeRecursivelyParsedUsingJacksonBindPom() throws Exception {
        var dataBind = readPom("jackson-databind-2.12.4.pom");
        var base = readPom("jackson-base-2.12.4.pom");
        var bom = readPom("jackson-bom-2.12.4.pom");

        var pom = dataBind
                .withParent(base
                        .withParent(bom));

        assertThat(pom).has(dependencies(
                dep("com.fasterxml.jackson.core", "jackson-annotations", "2.12.4"),
                dep("com.fasterxml.jackson.core", "jackson-core", "2.12.4"),
                dep("org.powermock", "powermock-core", "2.0.0", TEST),
                dep("org.powermock", "powermock-module-junit4", "2.0.0", TEST),
                dep("org.powermock", "powermock-api-mockito2", "2.0.0", TEST),
                dep("junit", "junit", "", TEST), // WTF no version specified anywhere?!
                dep("javax.measure", "jsr-275", "0.9.1", TEST)
        )).has(artifactCoordinates(new Artifact("com.fasterxml.jackson.core", "jackson-databind", "2.12.4")));
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
    void canParseOptionalDependencies() throws Exception {
        var pom = readPom("optional/optional-deps.pom.xml");
        var parent = readPom("optional/optional-parent.pom.xml");

        assertThat(pom.withParent(parent))
                .has(dependencies(
                        dep("foo", "bar", "1", COMPILE, true),
                        dep("goo", "ya", "4", COMPILE, true),
                        dep("zort", "boo", "2", TEST)
                ))
                .has(dependencyManagement(
                        dep("foo", "bar", "1"),
                        dep("goo", "ya", "4")
                ))
                .has(artifactCoordinates(new Artifact("com.test", "optional-deps", "1.1")));

    }

    @Test
    void canParseLicenses() throws Exception {
        var junit = readPom("junit.pom.xml");

        assertThat(junit).has(licenses(new License(
                "Eclipse Public License 1.0",
                "http://www.eclipse.org/legal/epl-v10.html")));

        var guavaParent = readPom("guava-parent.pom.xml");

        assertThat(guavaParent).has(licenses(new License(
                "Apache License, Version 2.0",
                "http://www.apache.org/licenses/LICENSE-2.0.txt")));
    }

    @Test
    void dependencyInheritsVersionAndScopeFromDependencyManagement() throws Exception {
        var pom = readPom("collections-0.4.0.pom");
        var parent = readPom("parent-0.4.0.pom");

        assertThat(pom.withParent(parent))
                .has(dependencies(
                        dep("br.com.objectos", "assertion", "0.7.0", TEST)
                ))
                .has(dependencyManagement(
                        dep("br.com.objectos", "assertion", "0.7.0", TEST)
                ))
                .has(artifactCoordinates(new Artifact("br.com.objectos.core", "collections", "0.4.0")));

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
                new Example("${${bar}}", Map.of("${bar}", "${foo}", "foo", "yes"), "yes"),
                new Example("${bar}${foo}", Map.of("foo", "no", "bar", "zort"), "${bar}${foo}")
        ).forEach(example ->
                assertThat(MavenUtils.resolveProperty(example.value, example.properties))
                        .isEqualTo(example.expectedResolvedValue)
        );
    }

    @Test
    void canDetectInfiniteLoopInMavenProperties() {
        assertThatThrownBy(() ->
                MavenUtils.resolveProperty("${foo}", Map.of("foo", "${foo}"))
        ).isInstanceOf(IllegalStateException.class)
                .hasMessage("infinite loop detected resolving property: foo -> foo");

        assertThatThrownBy(() ->
                MavenUtils.resolveProperty("${foo}", Map.of(
                        "foo", "${bar}",
                        "bar", "${zort}",
                        "zort", "${wat}",
                        "wat", "${bar}"
                ))
        ).isInstanceOf(IllegalStateException.class)
                .hasMessage("infinite loop detected resolving property: foo -> bar -> zort -> wat -> bar");
    }

}
