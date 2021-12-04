package jbuild.errors;

import jbuild.artifact.Artifact;

public interface ArtifactRetrievalError {

    Artifact getArtifact();

    void describe(StringBuilder builder, boolean verbose);

    default String getDescription() {
        var builder = new StringBuilder();
        describe(builder, false);
        return builder.toString();
    }
}
