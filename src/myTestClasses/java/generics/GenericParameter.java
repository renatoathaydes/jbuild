package generics;

import java.util.concurrent.Callable;
import java.util.function.Function;

public interface GenericParameter<T, V> extends Function<Generics<? extends BaseA>, Callable<T>> {
}
