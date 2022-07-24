package jbuild.artifact;

import jbuild.artifact.file.FileArtifactRetriever;
import jbuild.artifact.http.HttpArtifactRetriever;
import jbuild.log.JBuildLog;
import jbuild.util.NonEmptyCollection;

public final class DefaultArtifactRetrievers {

    public static NonEmptyCollection<? extends ArtifactRetriever<?>> get(JBuildLog log) {
        return NonEmptyCollection.of(
                NonEmptyCollection.of(new FileArtifactRetriever()),
                new HttpArtifactRetriever(log));
    }
}
