package jbuild.artifact.file;

import jbuild.artifact.ResolvedArtifact;
import jbuild.util.Describable;
import jbuild.util.Either;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.concurrent.CompletableFuture.completedStage;
import static jbuild.maven.MavenUtils.standardArtifactPath;

public class ArtifactFileWriter implements AutoCloseable, Closeable {

    public enum WriteMode {
        FLAT_DIR,
        MAVEN_REPOSITORY,
    }

    public final File directory;
    public final WriteMode mode;
    private final ExecutorService writerExecutor;

    public ArtifactFileWriter(File directory, WriteMode mode) {
        this.directory = directory;
        this.mode = mode;
        this.writerExecutor = Executors.newSingleThreadExecutor((runnable) -> {
            var thread = new Thread(runnable, directory + "-output-writer");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletionStage<Either<File, Describable>> write(ResolvedArtifact resolvedArtifact, boolean consume) {
        var completion = new CompletableFuture<Either<File, Describable>>();
        var file = computeFileLocation(resolvedArtifact);

        // this may return false if the dir already exists or if running in parallel with another call that
        // creates this dir before us, so we cannot check the result here.
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();

        if (!file.getParentFile().isDirectory()) {
            return completedStage(
                    Either.right(Describable.of("unable to create directory at " + file.getParent())));
        }

        writerExecutor.submit(() -> {
            try (var out = new FileOutputStream(file)) {
                try {
                    if (consume) {
                        resolvedArtifact.consumeContents(out);
                    } else {
                        out.write(resolvedArtifact.getContents());
                    }
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

    private File computeFileLocation(ResolvedArtifact resolvedArtifact) {
        switch (mode) {
            case FLAT_DIR:
                return new File(directory, resolvedArtifact.artifact.toFileName());
            case MAVEN_REPOSITORY:
                var artifact = resolvedArtifact.artifact;
                return Path.of(
                        directory.getPath(), standardArtifactPath(artifact, true)
                ).toFile();
            default:
                throw new IllegalStateException("unknown enum variant: " + mode);
        }
    }

    @Override
    public void close() {
        writerExecutor.shutdownNow();
    }
}
