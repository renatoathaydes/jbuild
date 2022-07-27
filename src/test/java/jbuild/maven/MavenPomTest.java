package jbuild.maven;

import jbuild.artifact.Artifact;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;
import static jbuild.maven.MavenHelper.dep;
import static jbuild.maven.MavenHelper.readPom;
import static jbuild.maven.Scope.COMPILE;
import static jbuild.maven.Scope.RUNTIME;
import static jbuild.maven.Scope.TEST;
import static org.assertj.core.api.Assertions.assertThat;

public class MavenPomTest {

    @Test
    void mavenPomCanListItsDependencies() throws Exception {
        var pom = readPom("optional/optional-deps.pom.xml");

        assertThat(pom.getDependencies()).isEqualTo(Set.of(
                dep("foo", "bar", "", COMPILE, true),
                dep("goo", "ya", "", COMPILE, true),
                dep("zort", "boo", "2", TEST, false)
        ));

        assertThat(pom.getDependencies(EnumSet.of(COMPILE), true)).isEqualTo(Set.of(
                dep("foo", "bar", "", COMPILE, true),
                dep("goo", "ya", "", COMPILE, true)
        ));

        assertThat(pom.getDependencies(EnumSet.of(RUNTIME, COMPILE), true)).isEqualTo(Set.of(
                dep("foo", "bar", "", COMPILE, true),
                dep("goo", "ya", "", COMPILE, true)
        ));

        assertThat(pom.getDependencies(EnumSet.of(TEST), true)).isEqualTo(Set.of(
                dep("zort", "boo", "2", TEST, false)
        ));

        assertThat(pom.getDependencies(EnumSet.of(COMPILE, TEST), false)).isEqualTo(Set.of(
                dep("zort", "boo", "2", TEST, false)
        ));

        assertThat(pom.getDependencies(EnumSet.noneOf(Scope.class), true)).isEmpty();
    }

    @Test
    void pomProperties() throws Exception {
        var pom = readPom("optional/optional-deps.pom.xml");

        assertThat(pom.getProperties())
                .isEqualTo(Map.ofEntries(
                        entry("optionalYa", "true"),
                        entry("project.groupId", "com.test"),
                        entry("pom.groupId", "com.test"),
                        entry("project.artifactId", "optional-deps"),
                        entry("pom.artifactId", "optional-deps"),
                        entry("project.version", "1.1"),
                        entry("pom.version", "1.1"),
                        entry("project.parent.groupId", "com.test"),
                        entry("pom.parent.groupId", "com.test"),
                        entry("project.parent.artifactId", "optional-parent"),
                        entry("pom.parent.artifactId", "optional-parent"),
                        entry("project.parent.version", "1.1"),
                        entry("pom.parent.version", "1.1")
                ));
    }

    @Test
    void pomParent() throws Exception {
        var pom = readPom("optional/optional-deps.pom.xml");

        assertThat(pom.getParentArtifact())
                .isPresent()
                .get()
                .isEqualTo(new Artifact("com.test", "optional-parent", "1.1"));
    }

    @Test
    void pomPackaging() throws Exception {
        var pom = readPom("apache-23.pom");
        assertThat(pom.getPackaging()).isEqualTo("pom");

        pom = readPom("child.pom.xml"); // implicit packaging
        assertThat(pom.getPackaging()).isEqualTo("jar");

        pom = readPom("guava.pom.xml");
        assertThat(pom.getPackaging()).isEqualTo("bundle");
    }
}
