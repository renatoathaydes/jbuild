package jbuild.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class CollectionUtils {

    public static <T> Iterable<T> append(T first, Iterable<T> rest) {
        return () -> new Iterator<>() {
            T firstItem = first;
            final Iterator<T> delegate = rest.iterator();

            @Override
            public boolean hasNext() {
                return firstItem != null || delegate.hasNext();
            }

            @Override
            public T next() {
                if (firstItem != null) {
                    var result = firstItem;
                    firstItem = null;
                    return result;
                }
                return delegate.next();
            }
        };
    }

    public static <T> Iterable<T> append(Iterable<T> iter, T last) {
        return () -> new Iterator<>() {
            final Iterator<T> delegate = iter.iterator();
            boolean done = false;

            @Override
            public boolean hasNext() {
                return !done;
            }

            @Override
            public T next() {
                if (done) {
                    throw new NoSuchElementException();
                }
                if (delegate.hasNext()) {
                    return delegate.next();
                }
                done = true;
                return last;
            }
        };
    }

    public static <T> Iterable<T> append(Iterable<T> front, Iterable<T> back) {
        return () -> new Iterator<>() {
            Iterator<T> delegate = front.iterator();
            boolean doneFirst = false;

            @Override
            public boolean hasNext() {
                if (delegate == null) return false;
                if (delegate.hasNext()) {
                    return true;
                } else if (!doneFirst) {
                    delegate = back.iterator();
                    doneFirst = true;
                    return hasNext();
                }
                delegate = null;
                return false;
            }

            @Override
            public T next() {
                if (delegate == null) {
                    throw new NoSuchElementException();
                }
                return delegate.next();
            }
        };
    }

    public static <T> Set<T> union(Set<T> set1, Set<T> set2) {
        if (set1.isEmpty()) return set2;
        if (set2.isEmpty()) return set1;
        var result = new HashSet<T>(set1.size() + set2.size());
        result.addAll(set1);
        result.addAll(set2);
        return result;
    }

    public static <K, V> Map<K, V> union(Map<K, V> map1, Map<K, V> map2) {
        if (map1.isEmpty()) return map2;
        if (map2.isEmpty()) return map1;
        var result = new HashMap<K, V>(map1.size() + map2.size());
        result.putAll(map1);
        result.putAll(map2);
        return result;
    }

    public static <K, A, B> Map<K, B> mapValues(Map<K, A> map, Function<A, B> transform) {
        var result = new HashMap<K, B>(map.size());
        for (var entry : map.entrySet()) {
            result.put(entry.getKey(), transform.apply(entry.getValue()));
        }
        return result;
    }

    public static <K, A, B> Map<K, B> mapEntries(Map<K, A> map, BiFunction<K, A, B> transform) {
        var result = new HashMap<K, B>(map.size());
        for (var entry : map.entrySet()) {
            result.put(entry.getKey(), transform.apply(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private static <T, S> Optional<S> find(Iterable<T> iterable,
                                           Function<T, S> transform) {
        for (T item : iterable) {
            var mapped = transform.apply(item);
            if (mapped != null) {
                return Optional.of(mapped);
            }
        }
        return Optional.empty();
    }

    public static <T, E> Either<T, NonEmptyCollection<E>> foldEither(
            Iterable<Either<T, NonEmptyCollection<E>>> eitherIterable) {
        {
            var left = find(eitherIterable, e -> e.map(ok -> ok, err -> null));
            if (left.isPresent()) {
                return Either.left(left.get());
            }
        }

        var iter = eitherIterable.iterator();
        var result = iter.next();
        while (iter.hasNext()) {
            //noinspection OptionalGetWithoutIsPresent
            result = result.combineRight(iter.next(), NonEmptyCollection::of).get();
        }
        return result;
    }

    public static <T> Iterable<T> sorted(Collection<T> collection, Comparator<T> comparator) {
        var mutable = new ArrayList<>(collection);
        mutable.sort(comparator);
        return mutable;
    }
}
