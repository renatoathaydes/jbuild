package jbuild.artifact.file;

import jbuild.artifact.ResolvedArtifact;
import jbuild.commands.MavenPomRetriever;
import jbuild.commands.MavenPomRetriever.DefaultPomCreator;
import jbuild.errors.JBuildException;
import jbuild.maven.MavenPom;
import jbuild.util.AsyncUtils;
import jbuild.util.Describable;
import jbuild.util.Either;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.concurrent.CompletableFuture.completedStage;
import static jbuild.maven.MavenUtils.standardArtifactPath;

public class ArtifactFileWriter implements AutoCloseable, Closeable, MavenPomRetriever.PomCreator {

    public enum WriteMode {
        FLAT_DIR,
        MAVEN_REPOSITORY,
    }

    private final File directory;
    protected final WriteMode mode;
    private final ExecutorService writerExecutor;

    // keep track of written files to prevent writing files again and again
    private final Set<String> writtenAbsolutePaths = new HashSet<>();

    protected ArtifactFileWriter(ArtifactFileWriter copy) {
        this.directory = copy.directory;
        this.mode = copy.mode;
        this.writerExecutor = copy.writerExecutor;
    }

    public ArtifactFileWriter(File directory, WriteMode mode) {
        this.directory = directory;
        this.mode = mode;
        this.writerExecutor = Executors.newSingleThreadExecutor((runnable) -> {
            var thread = new Thread(runnable, directory + "-output-writer");
            thread.setDaemon(true);
            return thread;
        });
    }

    public String getDestination() {
        return directory.getPath();
    }

    protected boolean shouldSkipWriting(ResolvedArtifact resolvedArtifact) {
        if (resolvedArtifact.retriever.isLocalFileRetriever()) {
            try {
                var source = resolvedArtifact.retriever.computeFileLocation(resolvedArtifact).getCanonicalPath();
                var target = computeFileLocation(resolvedArtifact).getCanonicalPath();
                return source.equals(target);
            } catch (IOException e) {
                throw new JBuildException("Unable to compute resolved artifact '" +
                    resolvedArtifact.artifact.getCoordinates() + "'s canonical path due to " + e,
                    JBuildException.ErrorCause.IO_READ);
            }
        }
        return false;
    }

    public CompletionStage<Either<List<File>, Describable>> write(ResolvedArtifact resolvedArtifact, boolean consume) {
        if (shouldSkipWriting(resolvedArtifact)) {
            return completedStage(Either.left(List.of()));
        }

        var file = computeFileLocation(resolvedArtifact);

        // this may return false if the dir already exists or if running in parallel with another call that
        // creates this dir before us, so we cannot check the result here.
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();

        if (!file.getParentFile().isDirectory()) {
            return completedStage(
                Either.right(Describable.of("unable to create directory at " + file.getParent())));
        }

        return AsyncUtils.getAsync(() -> {
            if (!writtenAbsolutePaths.add(file.getAbsolutePath())) {
                if (consume) {
                    resolvedArtifact.consumeContents();
                }
                return Either.left(List.of(file));
            }
            try (var out = new FileOutputStream(file)) {
                try {
                    if (consume) {
                        resolvedArtifact.consumeContents(out);
                    } else {
                        out.write(resolvedArtifact.getContents());
                    }
                    return Either.left(List.of(file));
                } catch (IOException e) {
                    return Either.right(Describable.of(
                        "unable to write to file " + file + " due to " + e));
                }
            } catch (IOException e) {
                return Either.right(Describable.of(
                    "unable to open file " + file + " for writing due to " + e));
            }
        }, writerExecutor);
    }

    @Override
    public CompletionStage<MavenPom> createPom(ResolvedArtifact resolvedArtifact, boolean consume) {
        switch (mode) {
            case MAVEN_REPOSITORY:
                // POMs are written to Maven repositories
                return write(resolvedArtifact, false)
                    .thenCompose(ignore -> DefaultPomCreator.INSTANCE.createPom(resolvedArtifact, consume));
            case FLAT_DIR:
                // POMs are not written to flat directories
                return DefaultPomCreator.INSTANCE.createPom(resolvedArtifact, consume);
            default:
                throw new IllegalStateException("Unhandled case: " + mode);
        }
    }

    @Override
    public void close() {
        writerExecutor.shutdownNow();
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
}
