package jbuild.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

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
    void canStream() {
        assertThat(NonEmptyCollection.of(4).stream().collect(toList()))
                .containsExactly(4);

        assertThat(NonEmptyCollection.of(List.of(2, 3), 4).stream().collect(toList()))
                .containsExactly(2, 3, 4);
    }
}
