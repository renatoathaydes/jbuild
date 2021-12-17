package jbuild.maven;

import org.junit.jupiter.api.Test;

import java.util.List;

import static jbuild.maven.MavenAssertions.artifacts;
import static jbuild.maven.MavenHelper.artifact;
import static org.assertj.core.api.Assertions.assertThat;

public class DependencyTreeTest {

    @Test
    void canFlattenTreeToSet() {
        var singleNodeTree = DependencyTree.childless(artifact("lonely", "node", "1.0"), null);

        assertThat(singleNodeTree.toSet())
                .has(artifacts(artifact("lonely", "node", "1.0", "pom")));

        var tree = DependencyTree.resolved(artifact("a", "a", "1"), null, List.of(
                DependencyTree.resolved(artifact("b", "b", "2"), null, List.of(
                        DependencyTree.childless(artifact("c", "c", "3"), null),
                        DependencyTree.childless(artifact("d", "d", "4"), null)
                )),
                DependencyTree.childless(artifact("e", "e", "5"), null)));

        assertThat(tree.toSet())
                .has(artifacts(artifact("a", "a", "1", "pom"),
                        artifact("b", "b", "2", "pom"),
                        artifact("c", "c", "3", "pom"),
                        artifact("d", "d", "4", "pom"),
                        artifact("e", "e", "5", "pom")));
    }
}
