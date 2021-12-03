package jbuild.maven;

import jbuild.artifact.Artifact;
import org.junit.jupiter.api.Test;

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

    private static Dependency dep(String groupId, String artifactId, String version, Scope scope) {
        return new Dependency(new Artifact(groupId, artifactId, version), scope);
    }

}
