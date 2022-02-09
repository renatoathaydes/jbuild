package jbuild.artifact;

import jbuild.errors.ArtifactRetrievalError;
import jbuild.util.Either;

import java.util.concurrent.CompletionStage;

public interface ArtifactRetriever<Err extends ArtifactRetrievalError> {

    String getDescription();

    CompletionStage<ArtifactResolution<Err>> retrieve(Artifact artifact);

    CompletionStage<Either<? extends ArtifactMetadata, Err>> fetchMetadata(Artifact artifact);
}
