package jbuild.cli;

import jbuild.DependenciesManager;
import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactResolution;
import jbuild.artifact.ResolvedArtifact;
import jbuild.errors.HttpError;
import jbuild.errors.JBuildException;
import jbuild.util.AsyncUtils;
import jbuild.util.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.toList;
import static jbuild.errors.JBuildException.ErrorCause.IO_WRITE;
import static jbuild.errors.JBuildException.ErrorCause.TIMEOUT;

final class CommandExecutor {

    static Collection<ResolvedArtifact> fetchArtifacts(
            List<Artifact> artifacts, File outDir, boolean verbose) {
        var dirExists = FileUtils.ensureDirectoryExists(outDir);
        if (!dirExists) {
            throw new JBuildException(
                    "Output directory does not exist and cannot be created: " + outDir.getPath(),
                    IO_WRITE);
        }

        var resolvedArtifacts = new ConcurrentLinkedQueue<ResolvedArtifact>();
        var dependenciesManager = new DependenciesManager();

        var writerExecutor = Executors.newSingleThreadExecutor((runnable) -> {
            var thread = new Thread(runnable, "command-executor-output-writer");
            thread.setDaemon(true);
            return thread;
        });

        var writeErrors = new ConcurrentLinkedQueue<String>();
        var fileSystemErrors = new ConcurrentLinkedQueue<ArtifactResolution<Throwable>>();

        AsyncUtils.waitForEach(dependenciesManager.fetchAllFromFileSystem(artifacts), Duration.ofSeconds(10))
                .forEach(resolution -> resolution.use(
                        resolved -> resolvedArtifacts.add(
                                handleResolved(writerExecutor, resolved, writeErrors, outDir, verbose)),
                        error -> fileSystemErrors.add(resolution)));

        var httpErrors = new ConcurrentLinkedQueue<ArtifactResolution<HttpError<byte[]>>>();

        if (!fileSystemErrors.isEmpty()) {
            var artifactsRemaining = fileSystemErrors.stream()
                    .map(r -> r.requestedArtifact)
                    .collect(toList());

            AsyncUtils.waitForEach(dependenciesManager.downloadAllByHttp(artifactsRemaining),
                            Duration.ofSeconds(5L * artifactsRemaining.size()))
                    .forEach(resolution -> resolution.use(
                            resolved -> resolvedArtifacts.add(
                                    handleResolved(writerExecutor, resolved, writeErrors, outDir, verbose)),
                            error -> httpErrors.add(resolution)));
        }

        writerExecutor.shutdown();

        try {
            var ok = writerExecutor.awaitTermination(10, TimeUnit.SECONDS);
            if (!ok) {
                throw new JBuildException("Could not terminate writing output within timeout", TIMEOUT);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (!httpErrors.isEmpty() || !writeErrors.isEmpty()) {
            throw new JBuildException(reportErrors(fileSystemErrors, httpErrors, writeErrors, verbose), IO_WRITE);
        }

        if (verbose) {
            System.out.println("All " + artifacts.size() +
                    " artifacts successfully downloaded to " + outDir.getPath());
        }

        return resolvedArtifacts;
    }

    private static ResolvedArtifact handleResolved(ExecutorService writerExecutor,
                                                   ResolvedArtifact resolvedArtifact,
                                                   ConcurrentLinkedQueue<String> writeErrors,
                                                   File outDir,
                                                   boolean verbose) {
        writerExecutor.execute(() -> writeArtifact(resolvedArtifact, outDir, writeErrors, verbose));
        return resolvedArtifact;
    }

    private static void writeArtifact(ResolvedArtifact resolvedArtifact,
                                      File outDir,
                                      Collection<String> errors,
                                      boolean verbose) {
        var file = new File(outDir, resolvedArtifact.artifact.toFileName());
        try (var out = new FileOutputStream(file)) {
            try {
                resolvedArtifact.consumeContents(out);
                if (verbose) {
                    System.out.println("Wrote artifact " + resolvedArtifact.artifact + " to " + file.getPath());
                }
            } catch (IOException e) {
                errors.add("unable to write to file " + file + " due to " + e);
            }
        } catch (IOException e) {
            errors.add("unable to open file " + file + " for writing due to " + e);
        }
    }

    private static String reportErrors(Collection<ArtifactResolution<Throwable>> fileSystemErrors,
                                       Collection<ArtifactResolution<HttpError<byte[]>>> httpErrors,
                                       Collection<String> writeErrors,
                                       boolean verbose) {
        // if a file-system error artifact did not cause a http-error, we don't need to report it
        var nonResolvedFileSystemErrors = intersectionOf(fileSystemErrors, httpErrors);
        var builder = new StringBuilder(4096);
        reportFileSystemErrors(nonResolvedFileSystemErrors, builder);
        reportHttpErrors(httpErrors, builder, verbose);
        reportWriteErrors(writeErrors, builder);
        return builder.toString();
    }

    private static Collection<ArtifactResolution<Throwable>> intersectionOf(
            Collection<ArtifactResolution<Throwable>> fileSystemErrors,
            Collection<ArtifactResolution<HttpError<byte[]>>> httpErrors) {
        var result = new ArrayList<ArtifactResolution<Throwable>>(fileSystemErrors.size());
        for (var fsError : fileSystemErrors) {
            for (var httpError : httpErrors) {
                if (fsError.requestedArtifact.equals(httpError.requestedArtifact)) {
                    result.add(fsError);
                    break;
                }
            }
        }
        return result;
    }

    private static void reportFileSystemErrors(Collection<ArtifactResolution<Throwable>> fileSystemErrors,
                                               StringBuilder builder) {
        for (var fileSystemError : fileSystemErrors) {
            var error = fileSystemError.getErrorUnchecked().getCause();
            String cause;
            if (error instanceof NoSuchFileException) {
                cause = " was not found in the local file system";
            } else {
                cause = " could not be read in the local file system due to " + error;
            }
            builder.append("WARNING: ").append(fileSystemError.requestedArtifact).append(cause).append('\n');
        }
    }

    private static void reportHttpErrors(Collection<ArtifactResolution<HttpError<byte[]>>> httpErrors,
                                         StringBuilder builder,
                                         boolean verbose) {
        for (var httpError : httpErrors) {
            var error = httpError.getErrorUnchecked();
            builder.append("ERROR: ")
                    .append(httpError.requestedArtifact)
                    .append(" could not be fetched from http repository: http-status=")
                    .append(error.httpResponse.statusCode())
                    .append(verbose
                            ? ", http-body = " + new String(error.httpResponse.body(), StandardCharsets.UTF_8)
                            : "")
                    .append('\n');
        }
    }

    private static void reportWriteErrors(Collection<String> writeErrors,
                                          StringBuilder builder) {
        for (var writeError : writeErrors) {
            builder.append("ERROR: ").append(writeError).append('\n');
        }
    }
}
