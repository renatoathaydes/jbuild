package jbuild.errors;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactRetriever;

public interface ArtifactRetrievalError {
    ArtifactRetriever<?> getRetriever();

    Artifact getArtifact();
}
