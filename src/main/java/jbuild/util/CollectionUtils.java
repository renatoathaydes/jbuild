package jbuild.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public final class CollectionUtils {

    public static <T> Iterable<T> append(Iterable<T> iter, T last) {
        return () -> new Iterator<T>() {
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

    public static <T, V> V firstMapping(Iterable<T> iterable, Function<T, V> transform, Supplier<V> defaultValue) {
        var iterator = iterable.iterator();
        if (iterator.hasNext()) {
            return transform.apply(iterator.next());
        }
        return defaultValue.get();
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

    public static <T> Either<T, List<Describable>> foldEither(
            Iterable<Either<T, Describable>> eitherIterable,
            int sizeHint) {
        var errors = new ArrayList<Describable>(sizeHint);

        for (var either : eitherIterable) {
            var result = either.map(success -> success, err -> {
                errors.add(err);
                return null;
            });
            if (result != null) {
                return Either.left(result);
            }
        }
        return Either.right(errors);
    }

    public static <T> Iterable<T> sorted(Collection<T> collection, Comparator<T> comparator) {
        var mutable = new ArrayList<>(collection);
        mutable.sort(comparator);
        return mutable;
    }
}
