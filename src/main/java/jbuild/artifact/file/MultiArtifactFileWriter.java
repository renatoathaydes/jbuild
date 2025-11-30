package jbuild.artifact.file;

import jbuild.artifact.Artifact;
import jbuild.artifact.ResolvedArtifact;
import jbuild.artifact.ResolvedArtifactChecksum;
import jbuild.maven.MavenPom;
import jbuild.util.Describable;
import jbuild.util.Either;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedStage;
import static jbuild.util.CollectionUtils.appendList;

public final class MultiArtifactFileWriter extends ArtifactFileWriter {

    public final ArtifactFileWriter secondWriter;

    public MultiArtifactFileWriter(ArtifactFileWriter primaryWriter, ArtifactFileWriter secondWriter) {
        super(primaryWriter);
        this.secondWriter = secondWriter;
    }

    @Override
    public CompletionStage<Either<List<File>, Describable>> write(ResolvedArtifact resolvedArtifact, boolean consume) {
        // the first writer must never consume the artifact! Only one writer must do it.
        return super.write(resolvedArtifact, false).thenCompose(res1 ->
                res1.map(files1 -> secondWriter.write(resolvedArtifact, consume).thenApply(res2 ->
                                res2.map(
                                        files2 -> Either.left(appendList(files1, files2)),
                                        Either::right)),
                        err1 -> completedStage(Either.right(err1))));
    }

    @Override
    public CompletionStage<MavenPom> createPom(ResolvedArtifact resolvedArtifact, boolean consume) {
        switch (mode) {
            case FLAT_DIR:
                // super wouldn't write the POM, so calling super is unnecessary
                return secondWriter.createPom(resolvedArtifact, consume);
            case MAVEN_REPOSITORY:
                switch (secondWriter.mode) {
                    case FLAT_DIR:
                        return super.createPom(resolvedArtifact, consume);
                    case MAVEN_REPOSITORY:
                        // both are Maven repositories, create the POM in both repos
                        return super.createPom(resolvedArtifact, false)
                                .thenCompose(ignore -> secondWriter.createPom(resolvedArtifact, consume));
                    default:
                        throw new IllegalStateException("Unhandled case: " + secondWriter.mode);
                }
            default:
                throw new IllegalStateException("Unhandled case: " + mode);
        }
    }

    @Override
    public CompletionStage<MavenPom> createPom(ResolvedArtifactChecksum resolvedArtifact, boolean consume) {
        switch (mode) {
            case FLAT_DIR:
                // super wouldn't write the POM, so calling super is unnecessary
                return secondWriter.createPom(resolvedArtifact, consume);
            case MAVEN_REPOSITORY:
                switch (secondWriter.mode) {
                    case FLAT_DIR:
                        return super.createPom(resolvedArtifact, consume);
                    case MAVEN_REPOSITORY:
                        // both are Maven repositories, create the POM in both repos
                        return super.createPom(resolvedArtifact, false)
                                .thenCompose(ignore -> secondWriter.createPom(resolvedArtifact, consume));
                    default:
                        throw new IllegalStateException("Unhandled case: " + secondWriter.mode);
                }
            default:
                throw new IllegalStateException("Unhandled case: " + mode);
        }
    }

    @Override
    public boolean delete(Artifact artifact) {
        return super.delete(artifact) && secondWriter.delete(artifact);
    }

    @Override
    public String getDestination() {
        var firstDestination = super.getDestination();
        var secondDestination = secondWriter.getDestination();
        return "[" + firstDestination + ", " + secondDestination + "]";
    }

    @Override
    public void close() {
        try {
            super.close();
        } finally {
            secondWriter.close();
        }
    }
}
