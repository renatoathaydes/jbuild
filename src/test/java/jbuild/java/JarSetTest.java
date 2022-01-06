package jbuild.java;

import jbuild.log.JBuildLog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static jbuild.util.CollectionUtils.mapValues;
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
        var set = computeUniqueJarSetPermutations(Map.of("foo", Set.of(file("j1"))));

        assertThat(set)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1")));

        set = computeUniqueJarSetPermutations(Map.of("foo", Set.of(file("j1"), file("j2"))));
        assertThat(set)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1"),
                        Map.of("foo", "j2")));

        set = computeUniqueJarSetPermutations(Map.of(
                "foo", Set.of(file("j1"), file("j2")),
                "bar", Set.of(file("j2"))));
        assertThat(set)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1"),
                        Map.of("foo", "j2", "bar", "j2")));

        set = computeUniqueJarSetPermutations(Map.of(
                "foo", Set.of(file("j1"), file("j2")),
                "bar", Set.of(file("j1"), file("j2"))));
        assertThat(set)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1", "bar", "j1"),
                        Map.of("foo", "j2", "bar", "j2")));

        set = computeUniqueJarSetPermutations(Map.of(
                "foo", Set.of(file("j1"), file("j2")),
                "bar", Set.of(file("j2")),
                "car", Set.of(file("j3"))));
        assertThat(set)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1", "car", "j3"),
                        Map.of("foo", "j2", "bar", "j2", "car", "j3")));

        set = computeUniqueJarSetPermutations(Map.of(
                "foo", Set.of(file("j1"), file("j2")),
                "bar", Set.of(file("j2"), file("j3")),
                "car", Set.of(file("j3"))));
        assertThat(set)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1"),
                        Map.of("foo", "j2", "bar", "j2"),
                        Map.of("bar", "j3", "car", "j3")));

        set = computeUniqueJarSetPermutations(Map.of(
                "foo", Set.of(file("j1"), file("j2")),
                "bar", Set.of(file("j1"), file("j2")),
                "car", Set.of(file("j1"), file("j2"))));
        assertThat(set)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1", "bar", "j1", "car", "j1"),
                        Map.of("foo", "j2", "bar", "j2", "car", "j2")));

        set = computeUniqueJarSetPermutations(Map.of(
                "foo", Set.of(file("j1"), file("j2")),
                "bar", Set.of(file("j2"), file("j3")),
                "car", Set.of(file("j1"), file("j2"), file("j3"))));
        assertThat(set)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1", "car", "j1"),
                        Map.of("bar", "j3", "car", "j3"),
                        Map.of("foo", "j2", "bar", "j2", "car", "j2")));

        set = computeUniqueJarSetPermutations(Map.of(
                "foo", Set.of(file("j1")),
                "bar", Set.of(file("j2")),
                "car", Set.of(file("j3"))));
        assertThat(set)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1", "bar", "j2", "car", "j3")));
    }

    @Test
    void canPruneConflictingJarsWhenSameJarsAppearInMultiplePlaces() {
        var set = computeUniqueJarSetPermutations(Map.of(
                "logger", Set.of(file("logger1"), file("logger2")),
                "logger_new", Set.of(file("logger2"), file("logger3")),
                "other", Set.of(file("other1"), file("other2"))));

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
        var set = new JarSet(Map.of("t1", file("j1"), "t2", file("j2"), "t3", file("j1")));

        assertThat(set.containsAny(Set.of())).isFalse();
        assertThat(set.containsAny(Set.of(new SimpleEntry<>(file("j1"), file("j3"))))).isFalse();
        assertThat(set.containsAny(Set.of(new SimpleEntry<>(file("j3"), file("j1"))))).isFalse();
        assertThat(set.containsAny(Set.of(new SimpleEntry<>(file("j2"), file("j3"))))).isFalse();
        assertThat(set.containsAny(Set.of(new SimpleEntry<>(file("j3"), file("j2"))))).isFalse();
        assertThat(set.containsAny(Set.of(new SimpleEntry<>(file("j1"), file("j2"))))).isTrue();
        assertThat(set.containsAny(Set.of(new SimpleEntry<>(file("j2"), file("j1"))))).isTrue();
        assertThat(set.containsAny(Set.of(
                new SimpleEntry<>(file("j2"), file("j1")),
                new SimpleEntry<>(file("j3"), file("j1"))))).isTrue();
        assertThat(set.containsAny(Set.of(
                new SimpleEntry<>(file("j1"), file("j3")),
                new SimpleEntry<>(file("j1"), file("j2"))))).isTrue();
        // Set.of() does not allow duplicates
        assertThat(set.containsAny(new HashSet<>(List.of(
                new SimpleEntry<>(file("j1"), file("j2")),
                new SimpleEntry<>(file("j1"), file("j2")))))).isTrue();
        assertThat(set.containsAny(Set.of(
                new SimpleEntry<>(file("j1"), file("j3")),
                new SimpleEntry<>(file("j3"), file("j2"))))).isFalse();
        assertThat(set.containsAny(Set.of(
                new SimpleEntry<>(file("a"), file("b")),
                new SimpleEntry<>(file("c"), file("d"))))).isFalse();
    }

    private static List<Map<String, String>> computeUniqueJarSetPermutations(Map<String, Set<File>> jarsByType) {
        var sets = new JarSet.Loader(log).computeUniqueJarSetPermutations(jarsByType);
        return sets.stream()
                .map(JarSet::getJarByType)
                .map(map -> mapValues(map, File::getPath))
                .collect(Collectors.toList());
    }

    private static File file(String path) {
        return new File(path);
    }

}
