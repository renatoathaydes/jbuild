package jbuild.util;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@FunctionalInterface
public interface AsyncSupplier<T> {
    T get() throws ExecutionException, CancellationException, InterruptedException, TimeoutException;
}
