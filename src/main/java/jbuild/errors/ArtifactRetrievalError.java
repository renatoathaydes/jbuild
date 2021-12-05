package jbuild.errors;

import jbuild.artifact.Artifact;
import jbuild.util.Describable;

public interface ArtifactRetrievalError extends Describable {

    Artifact getArtifact();

}
