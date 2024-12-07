package jbuild.util;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * No-op utilities.
 */
public final class NoOp {

    /**
     * @param <A> any type
     * @param <B> any type
     * @return a function that always returns null
     */
    @SuppressWarnings("unchecked")
    public static <A, B> Function<A, B> fun() {
        return (Function<A, B>) NullFun.INSTANCE;
    }

    /**
     * @param <A> any type
     * @return a predicate that always returns true
     */
    @SuppressWarnings("unchecked")
    public static <A> Predicate<A> all() {
        return (Predicate<A>) AllPredicate.INSTANCE;
    }

    /**
     * @param <A> any type
     * @return a consumer that ignores its input
     */
    @SuppressWarnings("unchecked")
    public static <A> Consumer<A> ignore() {
        return (Consumer<A>) IgnoreConsumer.INSTANCE;
    }

    @SuppressWarnings("unchecked")
    public static <A, B> BiConsumer<A, B> ignoreBoth() {
        return (BiConsumer<A, B>) IgnoreBiConsumer.INSTANCE;
    }

    private enum NullFun implements Function<Object, Object> {
        INSTANCE;

        @Override
        public Object apply(Object o) {
            return null;
        }
    }

    private enum AllPredicate implements Predicate<Object> {
        INSTANCE;

        @Override
        public boolean test(Object o) {
            return true;
        }
    }

    private enum IgnoreConsumer implements Consumer<Object> {
        INSTANCE;

        @Override
        public void accept(Object o) {
        }
    }

    private enum IgnoreBiConsumer implements BiConsumer<Object, Object> {
        INSTANCE;

        @Override
        public void accept(Object a, Object b) {
        }
    }
}
