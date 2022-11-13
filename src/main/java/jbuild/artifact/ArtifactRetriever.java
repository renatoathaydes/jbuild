package jbuild.artifact;

import jbuild.errors.ArtifactRetrievalError;
import jbuild.util.Either;

import java.io.File;
import java.util.concurrent.CompletionStage;

/**
 * Artifact retriever.
 * <p>
 * Can retrieve artifacts from local or remote repositories.
 *
 * @param <Err> type of error in case an artifact cannot be retrieved
 */
public interface ArtifactRetriever<Err extends ArtifactRetrievalError> {

    /**
     * @return user-readable description of this retriever.
     */
    String getDescription();

    /**
     * Retrieve the given artifact.
     * <p>
     * If the artifact has a version range, the latest version that satisfies the range is returned.
     *
     * @param artifact to retrieve
     * @return the retrieved artifact if successful, or a failed resolution otherwise
     */
    CompletionStage<ArtifactResolution<Err>> retrieve(Artifact artifact);

    /**
     * Retrieve metadata about an artifact.
     * <p>
     * The version and extension of the artifact are generally ignored.
     *
     * @param artifact to retrieve metadata about
     * @return metadata about the artifact if successful, or an error otherwise
     */
    CompletionStage<Either<? extends ArtifactMetadata, Err>> retrieveMetadata(Artifact artifact);

    /**
     * @return true if this retriever uses the local file system as source for artifacts.
     * If this returns {@code true}, then {@link ArtifactRetriever#computeFileLocation(ResolvedArtifact)}
     * should be implemented to return the local file for a given artifact.
     */
    boolean isLocalFileRetriever();

    /**
     * Compute the location of the local file for the resolved artifact.
     * <p>
     * This method only needs to be implemented in case {@link ArtifactRetriever#isLocalFileRetriever()}
     * return true.
     *
     * @param resolvedArtifact artifact
     */
    default File computeFileLocation(ResolvedArtifact resolvedArtifact) {
        throw new UnsupportedOperationException("computeFileLocation");
    }
}
