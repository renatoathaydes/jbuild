package jbuild.util;

import jbuild.errors.AsyncException;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

public class AsyncUtils {

    public static <T> Stream<T> waitForEach(List<? extends Future<T>> list) {
        return list.stream()
                .map(future -> handlingAsyncErrors(() -> future.get(10, TimeUnit.SECONDS)));
    }

    public static <T> T handlingAsyncErrors(AsyncSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw new AsyncException(e);
        }
    }
}
