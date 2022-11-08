package jbuild.artifact.file;

import jbuild.artifact.ResolvedArtifact;
import jbuild.util.Describable;
import jbuild.util.Either;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletionStage;

import static jbuild.util.CollectionUtils.appendList;

public final class MultiArtifactFileWriter extends ArtifactFileWriter {

    private final ArtifactFileWriter secondWriter;

    public MultiArtifactFileWriter(File directory, WriteMode mode, ArtifactFileWriter secondWriter) {
        super(directory, mode);
        this.secondWriter = secondWriter;
    }

    @Override
    public CompletionStage<Either<List<File>, Describable>> write(ResolvedArtifact resolvedArtifact, boolean consume) {
        var future1 = super.write(resolvedArtifact, consume);
        var future2 = secondWriter.write(resolvedArtifact, consume);
        return future1.thenCompose(res1 -> future2.thenApply(res2 -> res1.map(
            files1 -> res2.mapLeft(files2 -> appendList(files1, files2)),
            error1 -> res2.map(files2 -> Either.right(error1),
                error2 -> Either.right(Describable.of(error1, error2))))));
    }
}