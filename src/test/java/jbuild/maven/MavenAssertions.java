package jbuild.maven;

import jbuild.artifact.Artifact;
import jbuild.util.NonEmptyCollection;
import org.assertj.core.api.Condition;

import java.util.Collection;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

final class MavenAssertions {

    static Condition<? super MavenPom> dependencies(Dependency... dependencies) {
        var expectedArtifacts = Set.of(dependencies);
        return new Condition<>(pom -> pom.getDependencies().equals(expectedArtifacts),
                "dependencies %s", expectedArtifacts);
    }

    static Condition<? super Collection<? extends ResolvedDependency>> artifacts(Artifact... artifacts) {
        var expectedArtifacts = Set.of(artifacts);
        return new Condition<>(deps -> deps.stream()
                .map(d -> d.artifact)
                .collect(toSet())
                .equals(expectedArtifacts),
                "artifacts %s", expectedArtifacts);
    }

    static Condition<? super MavenPom> licenses(License... licenses) {
        var expectedLicenses = Set.of(licenses);
        return new Condition<>(pom -> pom.getLicenses().equals(expectedLicenses),
                "licenses %s", expectedLicenses);
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
