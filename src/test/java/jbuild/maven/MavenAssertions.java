package jbuild.maven;

import jbuild.artifact.Artifact;
import org.assertj.core.api.Condition;

import java.util.Set;

final class MavenAssertions {

    static Condition<? super MavenPom> dependencies(Dependency... dependencies) {
        var expectedArtifacts = Set.of(dependencies);
        return new Condition<>(pom -> pom.getDependencies().equals(expectedArtifacts),
                " dependencies %s", expectedArtifacts);
    }

    static Condition<? super MavenPom> artifactCoordinates(Artifact artifact) {
        return new Condition<>(pom -> pom.getArtifact().equals(artifact),
                " coordinates %s", artifact);
    }

}
