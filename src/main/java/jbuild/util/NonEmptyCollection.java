package jbuild.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static jbuild.util.CollectionUtils.append;

public final class NonEmptyCollection<T> implements Iterable<T> {

    public final T first;

    private final Iterable<T> all;

    private NonEmptyCollection(T first, Iterable<T> all) {
        this.first = first;
        this.all = all;
    }

    public List<T> take(int count) {
        var result = new ArrayList<T>(count);
        var i = 0;
        for (var item : this) {
            if (i >= count) break;
            result.add(item);
            i++;
        }
        return result;
    }

    public List<T> toList() {
        return stream().collect(Collectors.toList());
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

    public <V> NonEmptyCollection<V> map(Function<T, V> mapper) {
        V firstMapped = mapper.apply(first);
        var iterator = all.iterator();
        // throw away first item
        iterator.next();

        return new NonEmptyCollection<V>(firstMapped, () -> new Iterator<V>() {
            private boolean firstDone = false;

            @Override
            public boolean hasNext() {
                if (firstDone) return iterator.hasNext();
                return true;
            }

            @Override
            public V next() {
                if (!firstDone) {
                    firstDone = true;
                    return firstMapped;
                }
                return mapper.apply(iterator.next());
            }
        });
    }

    @Override
    public String toString() {
        return "NonEmptyCollection{" +
                stream().map(Object::toString).collect(joining(", ")) +
                '}';
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

    public static <T> NonEmptyCollection<T> of(Collection<T> list) {
        if (list.isEmpty()) {
            throw new IllegalArgumentException("empty list");
        }
        var iter = list.iterator();
        return new NonEmptyCollection<>(iter.next(), list);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        NonEmptyCollection<?> that = (NonEmptyCollection<?>) o;
        return first.equals(that.first) && all.equals(that.all);
    }

    @Override
    public int hashCode() {
        int result = first.hashCode();
        result = 31 * result + all.hashCode();
        return result;
    }
}
