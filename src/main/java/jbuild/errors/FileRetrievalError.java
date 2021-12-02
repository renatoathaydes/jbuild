package jbuild.errors;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactRetriever;

public class FileRetrievalError implements ArtifactRetrievalError {

    private final ArtifactRetriever<?> retriever;
    private final Artifact artifact;
    public final Throwable reason;

    public FileRetrievalError(ArtifactRetriever<?> retriever, Artifact artifact, Throwable reason) {
        this.retriever = retriever;
        this.artifact = artifact;
        this.reason = reason;
    }

    @Override
    public ArtifactRetriever<?> getRetriever() {
        return retriever;
    }

    @Override
    public Artifact getArtifact() {
        return artifact;
    }
}
