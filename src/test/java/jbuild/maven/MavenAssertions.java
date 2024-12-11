package jbuild.maven;

import jbuild.artifact.Artifact;
import jbuild.util.NoOp;
import jbuild.util.NonEmptyCollection;
import org.assertj.core.api.Condition;
import org.assertj.core.description.Description;
import org.assertj.core.description.JoinDescription;
import org.assertj.core.description.TextDescription;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

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
        return new CustomCondition<>(" dependencyManagement %s", expectedArtifacts) {
            {
                predicate = pom -> {
                    var actual = pom.getDependencyManagement()
                            .values().stream()
                            .flatMap(NonEmptyCollection::stream)
                            .collect(toSet());
                    var ok = actual.equals(expectedArtifacts);
                    if (!ok) {
                        if (actual.size() != expectedArtifacts.size()) {
                            extraDescriptions.add(new TextDescription("expected %d artifacts, got %d",
                                    expectedArtifacts.size(), actual.size()));
                        }
                        expectedArtifacts.stream().filter(a -> !actual.contains(a)).findFirst()
                                .ifPresent(different -> extraDescriptions.add(new TextDescription(
                                        "Expected item missing: %s", different
                                )));
                        actual.stream().filter(a -> !expectedArtifacts.contains(a)).findFirst()
                                .ifPresent(different -> extraDescriptions.add(new TextDescription(
                                        "Item not expected: %s", different
                                )));
                    }
                    return ok;
                };
            }
        };
    }

    static Condition<? super MavenPom> artifactCoordinates(Artifact artifact) {
        return new Condition<>(pom -> pom.getArtifact().equals(artifact),
                " coordinates %s", artifact);
    }

    private static class CustomCondition<T> extends Condition<T> {
        protected List<Description> extraDescriptions = new ArrayList<>();
        protected Predicate<T> predicate;

        public CustomCondition(String description, Object... args) {
            super(NoOp.all(), description, args);
        }

        @Override
        public boolean matches(T value) {
            return predicate.test(value);
        }

        @Override
        public Description description() {
            var result = new ArrayList<Description>(extraDescriptions.size() + 1);
            result.add(super.description());
            result.addAll(extraDescriptions);
            return new JoinDescription("====", "-----", result);
        }
    }

}
