package jbuild.maven;

import jbuild.artifact.Artifact;
import jbuild.util.NonEmptyCollection;
import org.assertj.core.api.Condition;

import java.util.Set;

import static java.util.stream.Collectors.toSet;

final class MavenAssertions {

    static Condition<? super MavenPom> dependencies(Dependency... dependencies) {
        var expectedArtifacts = Set.of(dependencies);
        return new Condition<>(pom -> pom.getDependencies().equals(expectedArtifacts),
                " dependencies %s", expectedArtifacts);
    }

    static Condition<? super MavenPom> dependencyManagement(Dependency... dependencies) {
        var expectedArtifacts = Set.of(dependencies);
        return new Condition<>(pom -> pom.getDependencyManagement()
                .values().stream()
                .flatMap(NonEmptyCollection::stream)
                .collect(toSet())
                .equals(expectedArtifacts),
                " dependencyManagement %s", expectedArtifacts);
    }

    static Condition<? super MavenPom> artifactCoordinates(Artifact artifact) {
        return new Condition<>(pom -> pom.getArtifact().equals(artifact),
                " coordinates %s", artifact);
    }

}
