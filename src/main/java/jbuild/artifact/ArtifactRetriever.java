package jbuild.artifact;

import java.util.concurrent.CompletableFuture;

public interface ArtifactRetriever<Err> {
    CompletableFuture<ArtifactResolution<Err>> retrieve(Artifact artifact);
}
