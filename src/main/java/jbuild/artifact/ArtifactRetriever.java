package jbuild.artifact;

import jbuild.errors.ArtifactRetrievalError;

import java.util.concurrent.CompletionStage;

public interface ArtifactRetriever<Err extends ArtifactRetrievalError> {

    String getDescription();

    CompletionStage<ArtifactResolution<Err>> retrieve(Artifact artifact);
}
