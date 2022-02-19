package jbuild.util;

import java.util.function.Function;

/**
 * No-op utilities.
 */
public final class NoOp {

    private static final Function<Object, Object> NULL_FUNCTION = (ignore) -> null;

    /**
     * @param <A> any type
     * @param <B> any type
     * @return a function that always returns null
     */
    @SuppressWarnings("unchecked")
    public static <A, B> Function<A, B> fun() {
        return (Function<A, B>) NULL_FUNCTION;
    }

}
