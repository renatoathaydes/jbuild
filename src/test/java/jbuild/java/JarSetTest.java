package jbuild.java;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class JarSetTest {

    @Test
    void canIdentifyUniqueTypesJarsPermutations() {
        var set = new JarSet(Map.of("foo", Set.of("j1")));
        assertThat(set.computeUniqueJarSetPermutations())
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1")));

        set = new JarSet(Map.of("foo", Set.of("j1", "j2")));
        assertThat(set.computeUniqueJarSetPermutations())
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1"),
                        Map.of("foo", "j2")));

        set = new JarSet(Map.of("foo", Set.of("j1", "j2"), "bar", Set.of("j2")));
        assertThat(set.computeUniqueJarSetPermutations())
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1"),
                        Map.of("foo", "j2", "bar", "j2")));

        set = new JarSet(Map.of("foo", Set.of("j1", "j2"), "bar", Set.of("j1", "j2")));
        assertThat(set.computeUniqueJarSetPermutations())
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1", "bar", "j1"),
                        Map.of("foo", "j2", "bar", "j2")));

        set = new JarSet(Map.of("foo", Set.of("j1", "j2"), "bar", Set.of("j2"), "car", Set.of("j3")));
        assertThat(set.computeUniqueJarSetPermutations())
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1", "car", "j3"),
                        Map.of("foo", "j2", "bar", "j2", "car", "j3")));

        set = new JarSet(Map.of(
                "foo", Set.of("j1", "j2"),
                "bar", Set.of("j2", "j3"),
                "car", Set.of("j3")));
        assertThat(set.computeUniqueJarSetPermutations())
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1", "bar", "j3", "car", "j3")));

        set = new JarSet(Map.of(
                "foo", Set.of("j1", "j2"),
                "bar", Set.of("j1", "j2"),
                "car", Set.of("j1", "j2")));
        assertThat(set.computeUniqueJarSetPermutations())
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1", "bar", "j1", "car", "j1"),
                        Map.of("foo", "j2", "bar", "j2", "car", "j2")));

        set = new JarSet(Map.of(
                "foo", Set.of("j1", "j2"),
                "bar", Set.of("j2", "j3"),
                "car", Set.of("j1", "j2", "j3")));
        assertThat(set.computeUniqueJarSetPermutations())
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1", "car", "j1"),
                        Map.of("bar", "j3", "car", "j3"),
                        Map.of("foo", "j2", "bar", "j2", "car", "j2")));

        set = new JarSet(Map.of(
                "foo", Set.of("j1"),
                "bar", Set.of("j2"),
                "car", Set.of("j3")));
        assertThat(set.computeUniqueJarSetPermutations())
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1", "bar", "j2", "car", "j3")));
    }

}
