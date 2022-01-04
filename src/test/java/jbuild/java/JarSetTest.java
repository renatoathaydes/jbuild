package jbuild.java;

import jbuild.log.JBuildLog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class JarSetTest {

    private static JBuildLog log;

    @BeforeAll
    static void beforeAll() {
        log = new JBuildLog(System.out, true);
//        log.setEnabled(false);
    }

    @Test
    void canIdentifyUniqueTypesJarsPermutations() {
        var set = new JarSet(log, Map.of("foo", Set.of("j1")));

        assertThat(set.computeUniqueJarSetPermutations())
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1")));

        set = new JarSet(log, Map.of("foo", Set.of("j1", "j2")));
        assertThat(set.computeUniqueJarSetPermutations())
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1"),
                        Map.of("foo", "j2")));

        set = new JarSet(log, Map.of("foo", Set.of("j1", "j2"), "bar", Set.of("j2")));
        assertThat(set.computeUniqueJarSetPermutations())
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1"),
                        Map.of("foo", "j2", "bar", "j2")));

        set = new JarSet(log, Map.of("foo", Set.of("j1", "j2"), "bar", Set.of("j1", "j2")));
        assertThat(set.computeUniqueJarSetPermutations())
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1", "bar", "j1"),
                        Map.of("foo", "j2", "bar", "j2")));

        set = new JarSet(log, Map.of("foo", Set.of("j1", "j2"), "bar", Set.of("j2"), "car", Set.of("j3")));
        assertThat(set.computeUniqueJarSetPermutations())
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1", "car", "j3"),
                        Map.of("foo", "j2", "bar", "j2", "car", "j3")));

        set = new JarSet(log, Map.of(
                "foo", Set.of("j1", "j2"),
                "bar", Set.of("j2", "j3"),
                "car", Set.of("j3")));
        assertThat(set.computeUniqueJarSetPermutations())
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1"),
                        Map.of("foo", "j2", "bar", "j2"),
                        Map.of("bar", "j3", "car", "j3")));

        set = new JarSet(log, Map.of(
                "foo", Set.of("j1", "j2"),
                "bar", Set.of("j1", "j2"),
                "car", Set.of("j1", "j2")));
        assertThat(set.computeUniqueJarSetPermutations())
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1", "bar", "j1", "car", "j1"),
                        Map.of("foo", "j2", "bar", "j2", "car", "j2")));

        set = new JarSet(log, Map.of(
                "foo", Set.of("j1", "j2"),
                "bar", Set.of("j2", "j3"),
                "car", Set.of("j1", "j2", "j3")));
        assertThat(set.computeUniqueJarSetPermutations())
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1", "car", "j1"),
                        Map.of("bar", "j3", "car", "j3"),
                        Map.of("foo", "j2", "bar", "j2", "car", "j2")));

        set = new JarSet(log, Map.of(
                "foo", Set.of("j1"),
                "bar", Set.of("j2"),
                "car", Set.of("j3")));
        assertThat(set.computeUniqueJarSetPermutations())
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("foo", "j1", "bar", "j2", "car", "j3")));
    }

    @Test
    void canPruneConflictingJarsWhenSameJarsAppearInMultiplePlaces() {
        var set = new JarSet(log, Map.of(
                "logger", Set.of("logger1", "logger2"),
                "logger_new", Set.of("logger2", "logger3"),
                "other", Set.of("other1", "other2")));

        assertThat(set.computeUniqueJarSetPermutations())
                .containsExactlyInAnyOrderElementsOf(List.of(
                        Map.of("logger", "logger1", "other", "other1"),
                        Map.of("logger", "logger1", "other", "other2"),
                        Map.of("logger", "logger2", "logger_new", "logger2", "other", "other1"),
                        Map.of("logger", "logger2", "logger_new", "logger2", "other", "other2"),
                        Map.of("logger_new", "logger3", "other", "other1"),
                        Map.of("logger_new", "logger3", "other", "other2")));
    }

}
