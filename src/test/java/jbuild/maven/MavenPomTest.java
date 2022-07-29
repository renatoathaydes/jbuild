package jbuild.maven;

import jbuild.artifact.Artifact;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Map.entry;
import static jbuild.maven.MavenHelper.dep;
import static jbuild.maven.MavenHelper.readPom;
import static jbuild.maven.Scope.COMPILE;
import static jbuild.maven.Scope.PROVIDED;
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

    @Test
    void complexPomHierarchy() throws Exception {
        var corePom = readPom("infinispan/infinispan-core-13.0.10.Final.pom");
        var jcachePom = readPom("infinispan/infinispan-jcache-13.0.10.Final.pom");
        var parentPom = readPom("infinispan/infinispan-parent-13.0.10.Final.pom");
        var buildConfigPom = readPom("infinispan/infinispan-build-configuration-parent-13.0.10.Final.pom");
        var bom = readPom("infinispan/infinispan-bom-13.0.10.Final.pom");

        var parent = parentPom.withParent(buildConfigPom).importing(bom);
        var core = corePom.withParent(parent);
        var jcache = jcachePom.withParent(parent);

        assertThat(core.getDependencies()).containsAll(List.of(
                dep("org.infinispan", "infinispan-commons", "13.0.10.Final", COMPILE),
                dep("org.infinispan", "infinispan-component-annotations", "13.0.10.Final", PROVIDED),
                dep("org.apache.geronimo.components", "geronimo-transaction", "3.1.1",
                        TEST, false, Set.of(ArtifactKey.of("org.apache.geronimo.specs", "geronimo-jta_1.1_spec")))));

        assertThat(core.getDependencies(EnumSet.of(COMPILE), false).stream()
                .map((dep) -> dep.artifact.getCoordinates()).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(
                        "com.github.ben-manes.caffeine:caffeine:2.8.4",
                        "org.infinispan:infinispan-commons:13.0.10.Final",
                        "jakarta.transaction:jakarta.transaction-api:1.3.3",
                        "org.jboss.logging:jboss-logging:3.4.1.Final",
                        "org.jboss.threads:jboss-threads:2.3.3.Final",
                        "org.jgroups:jgroups:4.2.18.Final",
                        "org.infinispan.protostream:protostream:4.4.3.Final",
                        "org.infinispan.protostream:protostream-types:4.4.3.Final"
                        // Maven resolves 1.3.0.Final but that's not what the parent POM says
//                        "org.wildfly.common:wildfly-common:1.5.4.Final"
                );


        assertThat(jcache.getDependencies()).containsAll(List.of(
                dep("org.infinispan", "infinispan-core", "13.0.10.Final", COMPILE),
                dep("org.infinispan", "infinispan-cdi-embedded", "13.0.10.Final", COMPILE, true)));
    }
}
