package jbuild.util;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.BiFunction;

import static java.util.concurrent.CompletableFuture.completedStage;

public final class AsyncUtils {

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

    public static <T, U> CompletionStage<U> handlingAsync(CompletableFuture<T> future,
                                                          BiFunction<T, Throwable, CompletionStage<U>> handler) {
        return future.handle(handler).thenCompose(x -> x);
    }

}
