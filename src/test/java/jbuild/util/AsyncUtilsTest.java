package jbuild.util;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.CompletableFuture.supplyAsync;
import static jbuild.util.CollectionUtils.mapEntries;
import static org.assertj.core.api.Assertions.assertThat;
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
