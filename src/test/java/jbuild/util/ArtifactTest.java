package jbuild.util;

import jbuild.artifact.Artifact;
import jbuild.artifact.Version;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArtifactTest {

    @Test
    void toFileName() {
        assertThat(new Artifact("g", "a", "v").toFileName())
                .isEqualTo("a-v.jar");
        assertThat(new Artifact("g", "artifact", "1.0-SNAPSHOT").toFileName())
                .isEqualTo("artifact-1.0-SNAPSHOT.jar");
        assertThat(new Artifact("g", "abc-def", "0.1", "pom").toFileName())
                .isEqualTo("abc-def-0.1.pom");
        assertThat(new Artifact("g", "abc-def", "2", "javadoc").toFileName())
                .isEqualTo("abc-def-2-javadoc.jar");
        assertThat(new Artifact("g", "abc-def", "2.1", "sources").toFileName())
                .isEqualTo("abc-def-2.1-sources.jar");
    }

    @Test
    void withExtension() {
        assertThat(new Artifact("g", "abc-def", "2.1")
                .withExtension("pom").toFileName())
                .isEqualTo("abc-def-2.1.pom");
        assertThat(new Artifact("g", "abc-def", "2.1", "sources")
                .withExtension("jar").toFileName())
                .isEqualTo("abc-def-2.1.jar");
    }

    @Test
    void withVersion() {
        assertThat(new Artifact("g", "abc-def", "2.1")
                .withVersion(Version.parse("3.0")).toFileName())
                .isEqualTo("abc-def-3.0.jar");
    }

    @Test
    void getCoordinates() {
        assertThat(new Artifact("g", "abc-def", "2.1").getCoordinates())
                .isEqualTo("g:abc-def:2.1");
        assertThat(new Artifact("groupId", "artifact", "version", "ext").getCoordinates())
                .isEqualTo("groupId:artifact:version");
    }

    @Test
    void parseCoordinates() {
        assertThat(Artifact.parseCoordinates("g:abc-def:2.1"))
                .isEqualTo(new Artifact("g", "abc-def", "2.1"));
        assertThat(Artifact.parseCoordinates("g:abc-def:3.0-SNAPSHOT.1:pom"))
                .isEqualTo(new Artifact("g", "abc-def", "3.0-SNAPSHOT.1", "pom"));
    }
}
