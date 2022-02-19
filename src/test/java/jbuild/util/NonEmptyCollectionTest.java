package jbuild.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class NonEmptyCollectionTest {

    @Test
    void canCreateFromOneItem() {
        assertThat(NonEmptyCollection.of("foo"))
                .containsExactly("foo");
    }

    @Test
    void canCombineTwoCollections() {
        assertThat(NonEmptyCollection.<Integer>of(NonEmptyCollection.of(1), List.of()))
                .containsExactly(1);

        assertThat(NonEmptyCollection.<Integer>of(NonEmptyCollection.of(1), List.of(2, 3)))
                .containsExactly(1, 2, 3);

        assertThat(NonEmptyCollection.of(List.of(), 4))
                .containsExactly(4);

        assertThat(NonEmptyCollection.of(List.of(2, 3), 4))
                .containsExactly(2, 3, 4);
    }

    @Test
    void canCreateFromList() {
        assertThat(NonEmptyCollection.of(List.of(1)))
                .containsExactly(1);

        assertThat(NonEmptyCollection.of(List.of(2, 3)))
                .containsExactly(2, 3);

        assertThatIllegalArgumentException().isThrownBy(() -> NonEmptyCollection.of(List.of()));
    }

    @Test
    void canStream() {
        assertThat(NonEmptyCollection.of(4).stream().collect(toList()))
                .containsExactly(4);

        assertThat(NonEmptyCollection.of(List.of(2, 3), 4).stream().collect(toList()))
                .containsExactly(2, 3, 4);
    }

    static Object[][] takeNExamples() {
        return new Object[][]{
                {List.of(1), 0, List.of()},
                {List.of(1), 1, List.of(1)},
                {List.of(1), 100, List.of(1)},
                {List.of("a", "b", "c"), 0, List.of()},
                {List.of("a", "b", "c"), 1, List.of("a")},
                {List.of("a", "b", "c"), 2, List.of("a", "b")},
                {List.of("a", "b", "c"), 3, List.of("a", "b", "c")},
                {List.of("a", "b", "c"), 4, List.of("a", "b", "c")},
                {List.of("a", "b", "c"), 100, List.of("a", "b", "c")},
        };
    }

    @MethodSource("takeNExamples")
    @ParameterizedTest
    <T> void takeN(List<T> list, int n, List<T> result) {
        assertThat(NonEmptyCollection.of(list).take(n))
                .containsExactlyElementsOf(result);
    }

    public static Object[][] toListExamples() {
        return new Object[][]{
                {NonEmptyCollection.of(1), List.of(1)},
                {NonEmptyCollection.of(List.of(), 2), List.of(2)},
                {NonEmptyCollection.of(List.of(1), 2), List.of(1, 2)},
                {NonEmptyCollection.of(List.of(1, 2, 3), 4), List.of(1, 2, 3, 4)},
                {NonEmptyCollection.of(List.of(1, 2, 3, 4)), List.of(1, 2, 3, 4)},
                {NonEmptyCollection.of(NonEmptyCollection.<Integer>of(1), List.of(2, 3, 4)), List.of(1, 2, 3, 4)},
                {NonEmptyCollection.of(List.of(), 0), List.of(0)},
        };
    }

    @MethodSource("toListExamples")
    @ParameterizedTest
    <T> void testToList(NonEmptyCollection<T> collection, List<T> result) {
        assertThat(collection.toList()).containsExactlyElementsOf(result);
    }
}
