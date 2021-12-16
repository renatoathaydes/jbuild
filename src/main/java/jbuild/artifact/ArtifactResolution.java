package jbuild.artifact;

import jbuild.errors.ArtifactRetrievalError;
import jbuild.util.Either;

/**
 * Resolution of an {@link Artifact}.
 * <p>
 * In case of success, this object will contain a {@link ResolvedArtifact}. In case of failure,
 * it will contain an instance of {@link Err}.
 *
 * @param <Err> error type
 */
public final class ArtifactResolution<Err extends ArtifactRetrievalError> {

    public final Either<ResolvedArtifact, Err> value;

    public ArtifactResolution(Either<ResolvedArtifact, Err> value) {
        this.value = value;
    }

    /**
     * Factory method for creating a successful {@link ResolvedArtifact}.
     *
     * @param resolvedArtifact the resolved artifact
     * @param <Err>            error type
     * @return successful {@link ResolvedArtifact}
     */
    public static <Err extends ArtifactRetrievalError> ArtifactResolution<Err> success(ResolvedArtifact resolvedArtifact) {
        return new ArtifactResolution<>(Either.left(resolvedArtifact));
    }

    /**
     * Factory method for creating a failed {@link ResolvedArtifact}.
     *
     * @param error the error
     * @param <Err> error type
     * @return failed {@link ResolvedArtifact}
     */
    public static <Err extends ArtifactRetrievalError> ArtifactResolution<Err> failure(Err error) {
        return new ArtifactResolution<>(Either.right(error));
    }
}
