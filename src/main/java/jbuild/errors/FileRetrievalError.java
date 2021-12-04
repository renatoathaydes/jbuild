package jbuild.errors;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactRetriever;

import java.io.FileNotFoundException;

public class FileRetrievalError implements ArtifactRetrievalError {

    private final ArtifactRetriever<FileRetrievalError> retriever;
    private final Artifact artifact;
    public final Throwable reason;

    public FileRetrievalError(ArtifactRetriever<FileRetrievalError> retriever,
                              Artifact artifact,
                              Throwable reason) {
        this.retriever = retriever;
        this.artifact = artifact;
        this.reason = reason;
    }

    @Override
    public Artifact getArtifact() {
        return artifact;
    }

    @Override
    public void describe(StringBuilder builder, boolean verbose) {
        builder.append(artifact);
        if (reason instanceof FileNotFoundException) {
            builder.append(" was not found in ")
                    .append(retriever.getDescription());
        } else {
            builder.append(" could not be read in ")
                    .append(retriever.getDescription())
                    .append(" due to ")
                    .append(reason);
        }
    }
}
