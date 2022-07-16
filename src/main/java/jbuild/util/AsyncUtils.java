package jbuild.util;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.completedStage;

public final class AsyncUtils {

    public static <T, V> Function<T, V> returning(V value, Consumer<T> consumer) {
        return (T t) -> {
            consumer.accept(t);
            return value;
        };
    }

    public static <K, V> CompletionStage<Map<K, Either<V, Throwable>>> awaitValues(
            Map<K, ? extends CompletionStage<V>> map) {
        if (map.isEmpty()) {
            return completedStage(Map.of());
        }

        var results = new ConcurrentHashMap<K, Either<V, Throwable>>(map.size());
        var future = new CompletableFuture<Map<K, Either<V, Throwable>>>();

        map.forEach((key, value) -> {
            value.whenComplete((ok, err) -> {
                results.put(key, err != null ? Either.right(err) : Either.left(ok));
                if (results.size() == map.size()) {
                    future.complete(results);
                }
            });
        });

        return future;
    }

    public static <T> CompletionStage<Collection<Either<T, Throwable>>> awaitValues(
            List<? extends CompletionStage<T>> list) {
        if (list.isEmpty()) {
            return completedStage(List.of());
        }

        var results = new ConcurrentLinkedDeque<Either<T, Throwable>>();
        var future = new CompletableFuture<Collection<Either<T, Throwable>>>();

        list.forEach(value -> value.whenComplete((ok, err) -> {
            results.add(err == null ? Either.left(ok) : Either.right(err));
            if (results.size() == list.size()) {
                future.complete(results);
            }
        }));

        return future;
    }

    public static <T> CompletionStage<Collection<T>> awaitSuccessValues(
            List<? extends CompletionStage<T>> list) {
        if (list.isEmpty()) {
            return completedStage(List.of());
        }

        var done = new AtomicBoolean(false);
        var results = new ConcurrentLinkedDeque<T>();
        var future = new CompletableFuture<Collection<T>>();

        list.forEach(value -> value.whenComplete((ok, err) -> {
            if (err != null) {
                if (done.compareAndSet(false, true)) {
                    future.completeExceptionally(err);
                }
            } else {
                results.add(ok);
                if (results.size() == list.size()) {
                    future.complete(results);
                }
            }
        }));

        return future;
    }

    @SuppressWarnings("unchecked")
    public static <K, T, V> CompletionStage<V> awaitValues(
            Map<K, CompletionStage<T>> map,
            Function<Map<K, T>, V> mapper) {
        Map<K, T> results = (Map<K, T>) map;
        var future = new CompletableFuture<V>();
        var remainingEntries = new AtomicInteger(map.size());

        for (Map.Entry<K, CompletionStage<T>> entry : map.entrySet()) {
            entry.getValue().whenComplete((ok, err) -> {
                if (future.isDone()) return;
                if (err != null) future.completeExceptionally(err);
                var entrySetter = (Map.Entry<K, T>) entry;
                entrySetter.setValue(ok);
                var remaining = remainingEntries.decrementAndGet();
                if (remaining == 0) {
                    future.complete(mapper.apply(results));
                }
            });
        }

        return future;
    }

    public static <T, U> CompletionStage<U> handlingAsync(CompletableFuture<T> future,
                                                          BiFunction<T, Throwable, CompletionStage<U>> handler) {
        return future.handle(handler).thenCompose(x -> x);
    }

}
