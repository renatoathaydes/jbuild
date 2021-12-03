package jbuild.artifact;

import jbuild.errors.ArtifactRetrievalError;

import java.util.concurrent.CompletableFuture;

public interface ArtifactRetriever<Err extends ArtifactRetrievalError> {

    String getDescription();

    CompletableFuture<ArtifactResolution<Err>> retrieve(Artifact artifact);
}
