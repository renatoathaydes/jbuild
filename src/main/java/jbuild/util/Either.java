package jbuild.util;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public final class Either<L, R> {

    private final L left;
    private final R right;

    private Either(L left, R right) {
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

    @SuppressWarnings("unchecked")
    public <T> Either<T, R> mapLeft(Function<L, T> withLeft) {
        if (right != null) {
            //noinspection unchecked
            return (Either<T, R>) this;
        }
        return Either.left(withLeft.apply(left));
    }

    @SuppressWarnings("unchecked")
    public <T> Either<L, T> mapRight(Function<R, T> withRight) {
        if (left != null) {
            //noinspection unchecked
            return (Either<L, T>) this;
        }
        return Either.right(withRight.apply(right));
    }

    public <T> Optional<Either<L, T>> combineRight(Either<L, R> other, BiFunction<R, R, T> combiner) {
        if (right != null && other.right != null) {
            return Optional.of(right(combiner.apply(this.right, other.right)));
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || !Either.class.equals(other.getClass())) return false;
        var otherEither = (Either<?, ?>) other;
        return ((left != null) && (otherEither.left != null) && left.equals(otherEither.left))
                || ((right != null) && (otherEither.right != null) && right.equals(otherEither.right));
    }

    @Override
    public int hashCode() {
        return left != null ? left.hashCode() : right.hashCode();
    }

    @Override
    public String toString() {
        return map(left -> "Either{left=" + left + "}", right -> "Either{right=" + right + "}");
    }
}
