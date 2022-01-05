package jbuild.java;

import jbuild.log.JBuildLog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap.SimpleEntry;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class JarSetTest {

    private static JBuildLog log;

    @BeforeAll
    static void beforeAll() {
        log = new JBuildLog(System.out, true);
        log.setEnabled(false);
    }

    @Test
    void canIdentifyUniqueTypesJarsPermutations() {
        var set = computeUniqueJarSetPermutations(Map.of("foo", Set.of("j1")));

        assertThat(set)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1")));

        set = computeUniqueJarSetPermutations(Map.of("foo", Set.of("j1", "j2")));
        assertThat(set)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1"),
                        Map.of("foo", "j2")));

        set = computeUniqueJarSetPermutations(Map.of("foo", Set.of("j1", "j2"), "bar", Set.of("j2")));
        assertThat(set)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1"),
                        Map.of("foo", "j2", "bar", "j2")));

        set = computeUniqueJarSetPermutations(Map.of("foo", Set.of("j1", "j2"), "bar", Set.of("j1", "j2")));
        assertThat(set)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1", "bar", "j1"),
                        Map.of("foo", "j2", "bar", "j2")));

        set = computeUniqueJarSetPermutations(Map.of("foo", Set.of("j1", "j2"), "bar", Set.of("j2"), "car", Set.of("j3")));
        assertThat(set)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1", "car", "j3"),
                        Map.of("foo", "j2", "bar", "j2", "car", "j3")));

        set = computeUniqueJarSetPermutations(Map.of(
                "foo", Set.of("j1", "j2"),
                "bar", Set.of("j2", "j3"),
                "car", Set.of("j3")));
        assertThat(set)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1"),
                        Map.of("foo", "j2", "bar", "j2"),
                        Map.of("bar", "j3", "car", "j3")));

        set = computeUniqueJarSetPermutations(Map.of(
                "foo", Set.of("j1", "j2"),
                "bar", Set.of("j1", "j2"),
                "car", Set.of("j1", "j2")));
        assertThat(set)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1", "bar", "j1", "car", "j1"),
                        Map.of("foo", "j2", "bar", "j2", "car", "j2")));

        set = computeUniqueJarSetPermutations(Map.of(
                "foo", Set.of("j1", "j2"),
                "bar", Set.of("j2", "j3"),
                "car", Set.of("j1", "j2", "j3")));
        assertThat(set)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1", "car", "j1"),
                        Map.of("bar", "j3", "car", "j3"),
                        Map.of("foo", "j2", "bar", "j2", "car", "j2")));

        set = computeUniqueJarSetPermutations(Map.of(
                "foo", Set.of("j1"),
                "bar", Set.of("j2"),
                "car", Set.of("j3")));
        assertThat(set)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1", "bar", "j2", "car", "j3")));
    }

    @Test
    void canPruneConflictingJarsWhenSameJarsAppearInMultiplePlaces() {
        var set = computeUniqueJarSetPermutations(Map.of(
                "logger", Set.of("logger1", "logger2"),
                "logger_new", Set.of("logger2", "logger3"),
                "other", Set.of("other1", "other2")));

        assertThat(set)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("logger", "logger1", "other", "other1"),
                        Map.of("logger", "logger1", "other", "other2"),
                        Map.of("logger", "logger2", "logger_new", "logger2", "other", "other1"),
                        Map.of("logger", "logger2", "logger_new", "logger2", "other", "other2"),
                        Map.of("logger_new", "logger3", "other", "other1"),
                        Map.of("logger_new", "logger3", "other", "other2")));
    }

    @Test
    void canCheckIfContainsJarPair() {
        var set = new JarSet(Map.of("t1", "j1", "t2", "j2", "t3", "j1"));

        assertThat(set.containsAny(Set.of())).isFalse();
        assertThat(set.containsAny(Set.of(new SimpleEntry<>("j1", "j3")))).isFalse();
        assertThat(set.containsAny(Set.of(new SimpleEntry<>("j3", "j1")))).isFalse();
        assertThat(set.containsAny(Set.of(new SimpleEntry<>("j2", "j3")))).isFalse();
        assertThat(set.containsAny(Set.of(new SimpleEntry<>("j3", "j2")))).isFalse();
        assertThat(set.containsAny(Set.of(new SimpleEntry<>("j1", "j2")))).isTrue();
        assertThat(set.containsAny(Set.of(new SimpleEntry<>("j2", "j1")))).isTrue();
        assertThat(set.containsAny(Set.of(new SimpleEntry<>("j2", "j1"), new SimpleEntry<>("j3", "j1")))).isTrue();
        assertThat(set.containsAny(Set.of(new SimpleEntry<>("j1", "j3"), new SimpleEntry<>("j1", "j2")))).isTrue();
        // Set.of() does not allow duplicates
        assertThat(set.containsAny(new HashSet<>(List.of(new SimpleEntry<>("j1", "j2"), new SimpleEntry<>("j1", "j2"))))).isTrue();
        assertThat(set.containsAny(Set.of(new SimpleEntry<>("j1", "j3"), new SimpleEntry<>("j3", "j2")))).isFalse();
        assertThat(set.containsAny(Set.of(new SimpleEntry<>("a", "b"), new SimpleEntry<>("c", "d")))).isFalse();
    }

    private static List<Map<String, String>> computeUniqueJarSetPermutations(Map<String, Set<String>> jarsByType) {
        var sets = new JarSet.Loader(log).computeUniqueJarSetPermutations(jarsByType);
        return sets.stream()
                .map(JarSet::getJarByType)
                .collect(Collectors.toList());
    }

}
