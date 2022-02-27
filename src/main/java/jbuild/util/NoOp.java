package jbuild.util;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * No-op utilities.
 */
public final class NoOp {

    private static final Function<Object, Object> NULL_FUNCTION = (ignore) -> null;

    private static final Predicate<Object> ALL_PREDICATE = (ignore) -> true;

    private static final Consumer<Object> IGNORE_CONSUMER = (ignore) -> {
    };

    /**
     * @param <A> any type
     * @param <B> any type
     * @return a function that always returns null
     */
    @SuppressWarnings("unchecked")
    public static <A, B> Function<A, B> fun() {
        return (Function<A, B>) NULL_FUNCTION;
    }

    /**
     * @param <A> any type
     * @return a predicate that always returns true
     */
    @SuppressWarnings("unchecked")
    public static <A> Predicate<A> all() {
        return (Predicate<A>) ALL_PREDICATE;
    }

    /**
     * @param <A> any type
     * @return a consumer that ignores its input
     */
    @SuppressWarnings("unchecked")
    public static <A> Consumer<A> ignore() {
        return (Consumer<A>) IGNORE_CONSUMER;
    }
}
