package jbuild.util;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static jbuild.util.CollectionUtils.append;

public final class NonEmptyCollection<T> implements Iterable<T> {

    public final T first;

    private final Iterable<T> all;

    private NonEmptyCollection(T first, Iterable<T> all) {
        this.first = first;
        this.all = all;
    }

    @Override
    public Iterator<T> iterator() {
        return all.iterator();
    }

    public Stream<T> stream() {
        var iter = iterator();
        return Stream.iterate(iter.next(),
                Objects::nonNull,
                ignore -> iter.hasNext() ? iter.next() : null);
    }

    public static <T> NonEmptyCollection<T> of(T item) {
        return new NonEmptyCollection<>(item, List.of(item));
    }

    public static <T> NonEmptyCollection<T> of(Iterable<T> head, T tail) {
        var iter = head.iterator();
        if (!iter.hasNext()) {
            return of(tail);
        }
        return new NonEmptyCollection<>(iter.next(), append(head, tail));
    }

    public static <T> NonEmptyCollection<T> of(NonEmptyCollection<T> head,
                                               Iterable<T> tail) {
        return new NonEmptyCollection<>(head.first, append(head, tail));
    }
}
