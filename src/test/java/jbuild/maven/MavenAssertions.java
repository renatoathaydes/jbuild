package jbuild.maven;

import jbuild.artifact.Artifact;
import org.assertj.core.api.Condition;

import java.util.List;

final class MavenAssertions {

    static Condition<? super MavenPom> dependencies(Dependency... dependencies) {
        var expectedArtifacts = List.of(dependencies);
        return new Condition<>(pom -> pom.getDependencies().equals(expectedArtifacts),
                " dependencies %s", expectedArtifacts);
    }

    static Condition<? super MavenPom> artifactCoordinates(Artifact artifact) {
        return new Condition<>(pom -> pom.getCoordinates().equals(artifact),
                " coordinates %s", artifact);
    }

}
