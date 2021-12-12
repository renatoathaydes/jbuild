package jbuild.util;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public final class Either<L, R> {

    private final L left;
    private final R right;

    private Either(L left, R right) {
        // one and only one arg may be null
        if ((left == null) == (right == null)) {
            throw new IllegalStateException(left == null ? "No option provided" : "Both options provided");
        }

        this.left = left;
        this.right = right;
    }

    public static <L, R> Either<L, R> left(L left) {
        return new Either<>(left, null);
    }

    public static <L, R> Either<L, R> right(R right) {
        return new Either<>(null, right);
    }

    public <T> T map(Function<L, T> withLeft,
                     Function<R, T> withRight) {
        if (left != null) {
            return withLeft.apply(left);
        }
        return withRight.apply(right);
    }

    public void use(Consumer<L> withLeft,
                    Consumer<R> withRight) {
        if (left != null) {
            withLeft.accept(left);
        } else {
            withRight.accept(right);
        }
    }

    public <T> Optional<Either<L, T>> combineRight(Either<L, R> other, BiFunction<R, R, T> combiner) {
        if (right != null && other.right != null) {
            return Optional.of(right(combiner.apply(this.right, other.right)));
        }
        return Optional.empty();
    }

}
