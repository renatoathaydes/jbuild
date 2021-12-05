package jbuild.artifact.file;

import jbuild.artifact.ResolvedArtifact;
import jbuild.util.Describable;
import jbuild.util.Either;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ArtifactFileWriter implements AutoCloseable, Closeable {

    private final File directory;
    private final ExecutorService writerExecutor;

    public ArtifactFileWriter(File directory) {
        this.directory = directory;
        this.writerExecutor = Executors.newSingleThreadExecutor((runnable) -> {
            var thread = new Thread(runnable, directory + "-output-writer");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletionStage<Either<File, Describable>> write(ResolvedArtifact resolvedArtifact) {
        var completion = new CompletableFuture<Either<File, Describable>>();
        var file = new File(directory, resolvedArtifact.artifact.toFileName());
        writerExecutor.submit(() -> {
            try (var out = new FileOutputStream(file)) {
                try {
                    resolvedArtifact.consumeContents(out);
                    completion.complete(Either.left(file));
                } catch (IOException e) {
                    completion.complete(Either.right(Describable.of(
                            "unable to write to file " + file + " due to " + e)));
                }
            } catch (IOException e) {
                completion.complete(Either.right(Describable.of(
                        "unable to open file " + file + " for writing due to " + e)));
            } catch (Exception e) {
                completion.completeExceptionally(e);
            }
        });
        return completion;
    }

    @Override
    public void close() {
        writerExecutor.shutdownNow();
    }
}
