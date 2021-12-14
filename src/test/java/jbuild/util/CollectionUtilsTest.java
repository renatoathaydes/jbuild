package jbuild.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static java.util.Comparator.comparingInt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class CollectionUtilsTest {

    @Test
    void canComputeSetUnion() {
        assertThat(CollectionUtils.union(Set.of(), Set.of())).isEmpty();
        assertThat(CollectionUtils.union(Set.of("foo"), Set.of())).isEqualTo(Set.of("foo"));
        assertThat(CollectionUtils.union(Set.of(), Set.of("bar"))).isEqualTo(Set.of("bar"));
        assertThat(CollectionUtils.union(Set.of("foo"), Set.of("bar"))).isEqualTo(Set.of("foo", "bar"));
        assertThat(CollectionUtils.union(Set.of("foo", "bar"), Set.of("bar", "foo"))).isEqualTo(Set.of("foo", "bar"));
    }

    @Test
    void canComputeMapUnion() {
        assertThat(CollectionUtils.union(Map.of(), Map.of())).isEmpty();
        assertThat(CollectionUtils.union(Map.of("foo", 1), Map.of()))
                .isEqualTo(Map.of("foo", 1));
        assertThat(CollectionUtils.union(Map.of("foo", 1), Map.of("bar", 2)))
                .isEqualTo(Map.of("foo", 1, "bar", 2));
        assertThat(CollectionUtils.union(Map.of("foo", 1, "bar", 3), Map.of("bar", 2)))
                .isEqualTo(Map.of("foo", 1, "bar", 2));
        assertThat(CollectionUtils.union(Map.of("foo", 1, "bar", 3), Map.of("bar", 2, "zort", 4)))
                .isEqualTo(Map.of("foo", 1, "bar", 2, "zort", 4));
    }

    @Test
    void canComputeMapUnionCombiningValues() {
        assertThat(CollectionUtils.union(Map.of(), Map.of(), (a, b) -> fail("should not call combiner"))).isEmpty();
        assertThat(CollectionUtils.union(Map.of("foo", 1), Map.of(), Integer::sum))
                .isEqualTo(Map.of("foo", 1));
        assertThat(CollectionUtils.union(Map.of("foo", 1), Map.of("bar", 2), Integer::sum))
                .isEqualTo(Map.of("foo", 1, "bar", 2));
        assertThat(CollectionUtils.union(Map.of("foo", 1, "bar", 3), Map.of("bar", 2), Integer::sum))
                .isEqualTo(Map.of("foo", 1, "bar", 5));
        assertThat(CollectionUtils.union(Map.of("foo", 1, "bar", 3),
                Map.of("bar", 2, "zort", 4, "foo", -4), Integer::sum))
                .isEqualTo(Map.of("foo", -3, "bar", 5, "zort", 4));
    }

    @Test
    void canAppendItemToIterable() {
        assertThat(CollectionUtils.append(List.of(), 1)).hasSameElementsAs(List.of(1));
        assertThat(CollectionUtils.append(List.of(2), 1)).hasSameElementsAs(List.of(2, 1));
        assertThat(CollectionUtils.append(List.of(2, 3), 4)).hasSameElementsAs(List.of(2, 3, 4));
    }

    @Test
    void canAppendIterableWithItem() {
        assertThat(CollectionUtils.append(1, List.of())).hasSameElementsAs(List.of(1));
        assertThat(CollectionUtils.append(1, List.of(2))).hasSameElementsAs(List.of(1, 2));
        assertThat(CollectionUtils.append(4, List.of(2, 3))).hasSameElementsAs(List.of(4, 2, 3));
    }

    @Test
    void canAppendIterableWithIterable() {
        assertThat(CollectionUtils.append(List.<Integer>of(), List.<Integer>of())).isEmpty();
        assertThat(CollectionUtils.append(List.<Integer>of(1), List.<Integer>of())).hasSameElementsAs(List.of(1));
        assertThat(CollectionUtils.append(List.<Integer>of(1), List.<Integer>of(2))).hasSameElementsAs(List.of(1, 2));
        assertThat(CollectionUtils.append(List.<Integer>of(), List.<Integer>of(0))).hasSameElementsAs(List.of(0));
        assertThat(CollectionUtils.append(List.<Integer>of(-1), List.<Integer>of(0, 1)))
                .hasSameElementsAs(List.of(-1, 0, 1));
        assertThat(CollectionUtils.append(List.<Integer>of(-10, -5, 0), List.<Integer>of(0, 1)))
                .hasSameElementsAs(List.of(-10, -5, 0, 0, 1));
    }

    @Test
    void canFoldEither() {
        assertThat(CollectionUtils.foldEither(List.of(Either.left(1))))
                .extracting(e -> e.map(ok -> ok, err -> -1))
                .isEqualTo(1);
        assertThat(CollectionUtils.foldEither(List.of(Either.right(NonEmptyCollection.of(1)))))
                .extracting(e -> e.map(l -> List.of(), CollectionUtilsTest::listOf))
                .isEqualTo(List.of(1));
        assertThat(CollectionUtils.foldEither(List.of(Either.right(NonEmptyCollection.of(List.of(1, 2), 3)))))
                .extracting(e -> e.map(l -> List.of(), CollectionUtilsTest::listOf))
                .isEqualTo(List.of(1, 2, 3));
        assertThat(CollectionUtils.foldEither(List.of(
                Either.right(NonEmptyCollection.of(List.of(1, 2), 3)),
                Either.right(NonEmptyCollection.of(List.of(4), 5)))))
                .extracting(e -> e.map(l -> List.of(), CollectionUtilsTest::listOf))
                .isEqualTo(List.of(1, 2, 3, 4, 5));
        assertThat(CollectionUtils.foldEither(List.of(
                Either.right(NonEmptyCollection.of(List.of(1, 2), 3)),
                Either.left("hello"),
                Either.right(NonEmptyCollection.of(List.of(4), 5)))))
                .extracting(e -> e.map(ok -> ok, err -> "ERROR:" + err))
                .isEqualTo("hello");
    }

    @Test
    void canSortList() {
        assertThat(CollectionUtils.sorted(
                List.of("1", "", "123456", "123", "1", "12"),
                comparingInt(String::length))
        ).hasSameElementsAs(List.of("", "1", "1", "12", "123", "123456"));
    }

    @Test
    void canMapValues() {
        assertThat(CollectionUtils.mapValues(Map.of(), Function.identity())).isEmpty();
        assertThat(CollectionUtils.mapValues(Map.of("foo", "bar"), String::toUpperCase))
                .isEqualTo(Map.of("foo", "BAR"));
        assertThat(CollectionUtils.mapValues(Map.of("foo", "bar", "bar", "foo"), String::toUpperCase))
                .isEqualTo(Map.of("foo", "BAR", "bar", "FOO"));
    }

    @Test
    void canMapEntries() {
        assertThat(CollectionUtils.mapEntries(Map.of(), (k, v) -> "")).isEmpty();
        assertThat(CollectionUtils.mapEntries(Map.of("foo", "bar"), String::concat))
                .isEqualTo(Map.of("foo", "foobar"));
        assertThat(CollectionUtils.mapEntries(Map.of("foo", "bar", "bar", "foo"), String::concat))
                .isEqualTo(Map.of("foo", "foobar", "bar", "barfoo"));
    }

    private static <T> List<T> listOf(Iterable<T> iter) {
        var result = new ArrayList<T>();
        for (T t : iter) {
            result.add(t);
        }
        return result;
    }
}
