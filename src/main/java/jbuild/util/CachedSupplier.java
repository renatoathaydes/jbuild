package jbuild.util;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public final class CachedSupplier<T> implements Supplier<T> {

    private final Supplier<T> supplier;
    private final AtomicReference<T> value = new AtomicReference<>();

    public CachedSupplier(Supplier<T> supplier) {
        this.supplier = requireNonNull(supplier);
    }

    @Override
    public T get() {
        var currentValue = value.get();
        if (currentValue == null) {
            synchronized (value) {
                currentValue = value.get();
                if (currentValue == null) {
                    currentValue = requireNonNull(supplier.get());
                    value.set(currentValue);
                }
            }
        }
        return currentValue;
    }
}
