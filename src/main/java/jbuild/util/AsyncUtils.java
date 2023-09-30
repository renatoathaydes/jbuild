package jbuild.util;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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

        var results = new ArrayList<Either<T, Throwable>>(list.size());
        var completedCount = new AtomicInteger(0);
        var future = new CompletableFuture<Collection<Either<T, Throwable>>>();

        for (int i = 0; i < list.size(); i++) {
            final var index = i;
            results.add(null);
            list.get(index).whenComplete((ok, err) -> {
                results.set(index, err == null ? Either.left(ok) : Either.right(err));
                var count = completedCount.incrementAndGet();
                if (count == list.size()) {
                    future.complete(results);
                }
            });
        }

        return future;
    }

    public static <T> CompletionStage<Collection<T>> awaitSuccessValues(
            List<? extends CompletionStage<T>> list) {
        if (list.isEmpty()) {
            return completedStage(List.of());
        }

        var failed = new AtomicBoolean(false);
        var completedCount = new AtomicInteger(0);
        var results = new ArrayList<T>(list.size());
        var future = new CompletableFuture<Collection<T>>();

        for (int i = 0; i < list.size(); i++) {
            final var index = i;
            results.add(null);
            list.get(index).whenComplete((ok, err) -> {
                if (err != null) {
                    if (failed.compareAndSet(false, true)) {
                        future.completeExceptionally(err);
                    }
                } else if (!failed.get()) {
                    results.set(index, ok);
                    var count = completedCount.incrementAndGet();
                    if (count == list.size()) {
                        future.complete(results);
                    }
                }
            });
        }

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

    public static <T, U> CompletionStage<U> handlingAsync(
            CompletionStage<T> future,
            BiFunction<T, Throwable, CompletionStage<U>> handler) {
        return future.handle(handler).thenCompose(x -> x);
    }

    public static <T, U> CompletionStage<U> withRetries(
            Supplier<CompletionStage<T>> future,
            int retries,
            Duration delayOnRetry,
            BiFunction<T, Throwable, CompletionStage<U>> handle) {
        CompletionStage<T> completionStage;
        try {
            completionStage = future.get();
        } catch (Throwable t) {
            return handle.apply(null, t);
        }
        return handlingAsync(completionStage, (T ok, Throwable err) -> {
            if (err != null) {
                return retries > 0
                        ? afterDelay(delayOnRetry, () -> withRetries(
                        future, retries - 1, delayOnRetry, handle))
                        : handle.apply(ok, err);
            }
            return handle.apply(ok, null);
        });
    }

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor((runnable) -> {
        var thread = new Thread(runnable, "jbuild-scheduler");
        thread.setDaemon(true);
        return thread;
    });

    @SuppressWarnings("FutureReturnValueIgnored")
    public static <T> CompletionStage<T> getAsync(Supplier<T> supplier, ExecutorService service) {
        var future = new CompletableFuture<T>();

        service.submit(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        return future;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public static CompletionStage<Void> runAsync(Runnable runnable) {
        var future = new CompletableFuture<Void>();

        scheduler.submit(() -> {
            try {
                runnable.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        return future;
    }

    public static <T> CompletionStage<T> runAsyncTiming(Supplier<T> supplier, BiConsumer<Duration, T> onSuccess) {
        var startTime = Instant.now();
        return CompletableFuture.supplyAsync(supplier).thenApply(result -> {
            onSuccess.accept(Duration.between(startTime, Instant.now()), result);
            return result;
        });
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public static <T> CompletionStage<T> afterDelay(Duration delay, Supplier<CompletionStage<T>> futureGetter) {
        var future = new CompletableFuture<T>();

        scheduler.schedule(() -> {
            try {
                futureGetter.get().whenComplete((ok, err) -> {
                    if (err == null) future.complete(ok);
                    else future.completeExceptionally(err);
                });
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);

        return future;
    }
}
