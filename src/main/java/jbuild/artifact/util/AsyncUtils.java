package jbuild.artifact.util;

import jbuild.errors.AsyncException;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.util.stream.Collectors.toList;

public class AsyncUtils {

    public static <T> List<T> waitForAll(List<? extends Future<T>> list) {
        return list.stream()
                .map(future -> handlingAsyncErrors(() -> future.get(10, TimeUnit.SECONDS)))
                .collect(toList());
    }

    public static <T> T handlingAsyncErrors(AsyncSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw new AsyncException(e);
        }
    }
}
