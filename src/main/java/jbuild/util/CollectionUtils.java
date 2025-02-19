package jbuild.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;

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

    public static <T> Stream<T> appendAsStream(Iterable<T> front, Iterable<T> back) {
        return StreamSupport.stream(append(front, back).spliterator(), false);
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

    public static <T> List<T> appendList(Iterable<T> front, Iterable<T> back) {
        return iterableToStream(append(front, back)).collect(toList());
    }

    public static <T> Stream<T> iterableToStream(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    public static <T> Set<T> append(Set<T> set, T last) {
        if (set.contains(last)) return set;
        var copy = new LinkedHashSet<T>(set.size() + 1);
        copy.addAll(set);
        copy.add(last);
        return copy;
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

    public static <K, V> Map<K, V> union(Map<K, V> map1, Map<K, V> map2, BiFunction<V, V, V> valueCombiner) {
        if (map1.isEmpty()) return map2;
        if (map2.isEmpty()) return map1;
        var result = new HashMap<K, V>(map1.size() + map2.size());
        for (var entry : map1.entrySet()) {
            var e2 = map2.get(entry.getKey());
            result.put(entry.getKey(), e2 == null
                    ? entry.getValue()
                    : valueCombiner.apply(entry.getValue(), e2));
        }
        for (var entry : map2.entrySet()) {
            if (result.containsKey(entry.getKey())) continue;
            var e1 = map1.get(entry.getKey());
            result.put(entry.getKey(), e1 == null
                    ? entry.getValue()
                    : valueCombiner.apply(e1, entry.getValue()));
        }
        return result;
    }

    public static <K, T> Map<K, T> filterValues(Map<K, T> map, Predicate<T> keep) {
        var result = new HashMap<K, T>();
        for (var entry : map.entrySet()) {
            if (keep.test(entry.getValue())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
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
            var left = find(eitherIterable, e -> e.map(Function.identity(), NoOp.fun()));
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

    public static <T, E> Either<T, NonEmptyCollection<E>> foldEither(
            Iterable<Either<T, NonEmptyCollection<E>>> eitherIterable,
            BiFunction<T, T, T> combiner) {
        T leftResults = null;
        for (var either : eitherIterable) {
            var value = either.map(Function.identity(), NoOp.fun());
            if (value == null) {
                leftResults = null;
                break;
            }
            if (leftResults == null) {
                leftResults = value;
            } else {
                leftResults = combiner.apply(leftResults, value);
            }
        }

        if (leftResults != null) return Either.left(leftResults);

        var iter = eitherIterable.iterator();
        var result = iter.next();
        while (iter.hasNext()) {
            var res = result.combineRight(iter.next(), NonEmptyCollection::of);
            if (res.isPresent()) result = res.get();
        }
        return result;
    }

    public static <T> Iterable<T> sorted(Collection<T> collection, Comparator<T> comparator) {
        var mutable = new ArrayList<>(collection);
        mutable.sort(comparator);
        return mutable;
    }

    public static <T> T lastOrDefault(Iterable<T> iterable, T defaultValue) {
        var last = defaultValue;
        for (var item : iterable) {
            last = item;
        }
        return last;
    }

    public static <K, V> Map<K, V> take(Map<K, V> map, int count) {
        if (map.size() <= count) return map;
        if (count <= 0) return Map.of();
        var result = new HashMap<K, V>(count);
        int index = 0;
        for (var entry : map.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
            if (++index == count) break;
        }
        return result;
    }
}
