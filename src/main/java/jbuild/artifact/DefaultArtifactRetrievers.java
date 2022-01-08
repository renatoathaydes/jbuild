package jbuild.artifact;

import jbuild.artifact.file.FileArtifactRetriever;
import jbuild.artifact.http.HttpArtifactRetriever;
import jbuild.util.NonEmptyCollection;

public final class DefaultArtifactRetrievers {

    private static final NonEmptyCollection<? extends ArtifactRetriever<?>> DEFAULT = NonEmptyCollection.of(
            NonEmptyCollection.of(new FileArtifactRetriever()),
            new HttpArtifactRetriever());

    public static NonEmptyCollection<? extends ArtifactRetriever<?>> get() {
        return DEFAULT;
    }
}
