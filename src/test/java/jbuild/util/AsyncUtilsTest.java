package jbuild.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static java.util.concurrent.CompletableFuture.completedStage;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static jbuild.util.CollectionUtils.mapEntries;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

public class AsyncUtilsTest {

    @Test
    void canAwaitOnMapOfCompletionStagesSuccess() throws InterruptedException {
        var map = Map.of(
                "hello", supplyAsync(() -> {
                    delay(4L);
                    return 10;
                }), "bye", supplyAsync(() -> {
                    delay(2L);
                    return 20;
                }));

        var completion = AsyncUtils.awaitValues(map);

        var result = new AtomicReference<Map<String, Either<Integer, Throwable>>>();
        var latch = new CountDownLatch(1);
        completion.whenComplete((ok, err) -> {
            if (err != null) throw new RuntimeException(err);
            result.set(ok);
            latch.countDown();
        });

        assert latch.await(2, TimeUnit.SECONDS);

        assertMapResults(Map.of("hello", 10, "bye", 20), result.get());
    }

    @Test
    void canAwaitOnMapOfCompletionStagesFailureAndSuccess() throws InterruptedException {
        Map<String, CompletionStage<Integer>> map = Map.of(
                "hello", supplyAsync(() -> 10),
                "bye", supplyAsync(() -> {
                    throw new EqualByMessageException("fail");
                }));

        var completion = AsyncUtils.awaitValues(map);

        var result = new AtomicReference<Map<String, Either<Integer, Throwable>>>();
        var latch = new CountDownLatch(1);
        completion.whenComplete((ok, err) -> {
            if (err != null) throw new RuntimeException(err);
            result.set(ok);
            latch.countDown();
        });

        assert latch.await(2, TimeUnit.SECONDS);

        assertMapResults(Map.of("hello", 10, "bye", new EqualByMessageException("fail")), result.get());
    }

    @Test
    void canAwaitOnMapOfCompletionStagesFailures() throws InterruptedException {
        Map<String, CompletionStage<Integer>> map = Map.of(
                "hello", supplyAsync(() -> {
                    throw new EqualByMessageException("fail1");
                }),
                "bye", supplyAsync(() -> {
                    throw new EqualByMessageException("fail2");
                }));

        var completion = AsyncUtils.awaitValues(map);

        var result = new AtomicReference<Map<String, Either<Integer, Throwable>>>();
        var latch = new CountDownLatch(1);
        completion.whenComplete((ok, err) -> {
            if (err != null) throw new RuntimeException(err);
            result.set(ok);
            latch.countDown();
        });

        assert latch.await(2, TimeUnit.SECONDS);

        assertMapResults(Map.of(
                        "hello", new EqualByMessageException("fail1"),
                        "bye", new EqualByMessageException("fail2")),
                result.get());
    }

    @Test
    void canRunChainOfAsyncActionsEvenWhenOneFails() {
        var shouldThrow = new AtomicBoolean(true);

        Supplier<String> futureHandler = () -> {
            if (shouldThrow.get()) throw new EqualByMessageException("not ok");
            return "no error";
        };

        BiFunction<String, Throwable, CompletionStage<Map<String, ?>>> secondAsyncAction = (ok, err) ->
                completedStage(err != null ? Map.of("error", err.getCause()) : Map.of("ok", ok));

        var asyncResult = AsyncUtils.handlingAsync(supplyAsync(futureHandler), secondAsyncAction);
        var result = blockAndGetResult(asyncResult);

        assertThat(result).isEqualTo(Map.of("error", new EqualByMessageException("not ok")));

        // when the first action doesn't throw an error, the second action gets the ok result
        shouldThrow.set(false);

        asyncResult = AsyncUtils.handlingAsync(supplyAsync(futureHandler), secondAsyncAction);
        result = blockAndGetResult(asyncResult);

        assertThat(result).isEqualTo(Map.of("ok", "no error"));
    }

    @Test
    void canRetryAsyncActionSuccessful() throws Exception {
        final var delayTime = 10;
        var runCount = new AtomicInteger(0);
        var times = new ConcurrentLinkedQueue<Long>();
        var result = AsyncUtils.withRetries(() -> supplyAsync(() -> {
            times.add(System.currentTimeMillis());
            var runs = runCount.getAndIncrement();
            if (runs == 0) {
                throw new RuntimeException("no");
            }
            return "ok";
        }), 2, Duration.ofMillis(delayTime), (ok, err) -> {
            if (err != null) return failedFuture(err);
            return completedStage(ok);
        }).toCompletableFuture().get(1, TimeUnit.SECONDS);

        assertThat(result).isEqualTo("ok");
        assertThat(times).hasSize(2);
        var timesIter = times.iterator();
        var firstTime = timesIter.next();
        var secondTime = timesIter.next();
        assertThat(secondTime).isBetween(firstTime + delayTime, firstTime + delayTime + 500);
    }

    @Test
    void canRetryAsyncActionFailing() {
        final var delayTime = 10;
        var times = new ConcurrentLinkedQueue<Long>();

        assertThatThrownBy(() -> AsyncUtils.withRetries(() -> supplyAsync(() -> {
            times.add(System.currentTimeMillis());
            throw new RuntimeException("no");
        }), 3, Duration.ofMillis(delayTime), (ok, err) -> {
            if (err != null) return failedFuture(err);
            return completedStage(ok);
        }).toCompletableFuture().get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(RuntimeException.class)
                .hasMessage("no");

        assertThat(times).hasSize(4);
        var timesIter = times.iterator();
        for (int i = 0; i < 2; i++) {
            var firstTime = timesIter.next();
            var secondTime = timesIter.next();
            assertThat(secondTime).isBetween(firstTime + delayTime, firstTime + delayTime + 500);
        }
    }

    private static void delay(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    private static <T> void assertMapResults(Map<String, Object> expected, Map<String, Either<T, Throwable>> actual) {
        var assertionMap = mapEntries(actual, (key, either) -> {
            var expectedValue = expected.get(key);
            if (expectedValue == null) fail("Actual Map contains unexpected key: " + key);
            var expectError = expectedValue instanceof Throwable;
            return either.map(
                    ok -> expectError ? fail("Got non-error where error expected: " + ok) : ok,
                    err -> expectError ? err.getCause() : fail("Got error where ok expected: " + err));
        });

        assertThat(assertionMap).isEqualTo(expected);
    }

    private <T> T blockAndGetResult(CompletionStage<T> completion) {
        var future = new CompletableFuture<T>();
        completion.handle((ok, err) -> {
            if (err != null) future.completeExceptionally(err);
            else future.complete(ok);
            return null;
        });

        T ok;
        try {
            ok = future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }

        if (ok == null) throw new RuntimeException("TIMEOUT");
        return ok;
    }
}

final class EqualByMessageException extends RuntimeException {

    public EqualByMessageException(String message) {
        super(message);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EqualByMessageException &&
                ((EqualByMessageException) other).getMessage().equals(getMessage());
    }
}
