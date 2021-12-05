package jbuild.artifact;

import jbuild.errors.ArtifactRetrievalError;
import jbuild.util.Either;

public final class ArtifactResolution<Err extends ArtifactRetrievalError> {

    public final Either<ResolvedArtifact, Err> value;

    public ArtifactResolution(Either<ResolvedArtifact, Err> value) {
        this.value = value;
    }

    public static <Err extends ArtifactRetrievalError> ArtifactResolution<Err> success(ResolvedArtifact resolvedArtifact) {
        return new ArtifactResolution<>(Either.left(resolvedArtifact));
    }

    public static <Err extends ArtifactRetrievalError> ArtifactResolution<Err> failure(Err error) {
        return new ArtifactResolution<>(Either.right(error));
    }
}
