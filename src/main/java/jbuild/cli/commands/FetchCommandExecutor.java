package jbuild.cli.commands;

import jbuild.DependenciesFetcher;
import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactResolution;
import jbuild.artifact.ResolvedArtifact;
import jbuild.errors.FileRetrievalError;
import jbuild.errors.HttpError;
import jbuild.errors.JBuildException;
import jbuild.log.JBuildLog;
import jbuild.util.AsyncUtils;
import jbuild.util.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

public final class FetchCommandExecutor {

    private final JBuildLog log;

    public FetchCommandExecutor(JBuildLog log) {
        this.log = log;
    }

    public Collection<ResolvedArtifact> fetchArtifacts(
            List<Artifact> artifacts, File outDir) {
        var dirExists = FileUtils.ensureDirectoryExists(outDir);
        if (!dirExists) {
            throw new JBuildException(
                    "Output directory does not exist and cannot be created: " + outDir.getPath(),
                    IO_WRITE);
        }

        var resolvedArtifacts = new ConcurrentLinkedQueue<ResolvedArtifact>();
        var fetcher = new DependenciesFetcher();

        var writerExecutor = Executors.newSingleThreadExecutor((runnable) -> {
            var thread = new Thread(runnable, "command-executor-output-writer");
            thread.setDaemon(true);
            return thread;
        });

        var writeErrors = new ConcurrentLinkedQueue<String>();
        var fileSystemErrors = new ConcurrentLinkedQueue<ArtifactResolution<FileRetrievalError>>();

        AsyncUtils.waitForEach(fetcher.fetchAllFromFileSystem(artifacts), Duration.ofSeconds(10))
                .forEach(resolution -> resolution.use(
                        resolved -> resolvedArtifacts.add(
                                handleResolved(writerExecutor, resolved, writeErrors, outDir)),
                        error -> fileSystemErrors.add(resolution)));

        var httpErrors = new ConcurrentLinkedQueue<ArtifactResolution<HttpError<byte[]>>>();

        if (!fileSystemErrors.isEmpty()) {
            var artifactsRemaining = fileSystemErrors.stream()
                    .map(r -> r.getErrorUnchecked().getArtifact())
                    .collect(toList());

            AsyncUtils.waitForEach(fetcher.fetchAllByHttp(artifactsRemaining),
                            Duration.ofSeconds(5L * artifactsRemaining.size()))
                    .forEach(resolution -> resolution.use(
                            resolved -> resolvedArtifacts.add(
                                    handleResolved(writerExecutor, resolved, writeErrors, outDir)),
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
            throw new JBuildException(
                    reportErrors(fileSystemErrors, httpErrors, writeErrors, log.isVerbose()),
                    IO_WRITE);
        }

        log.verbosePrintln(() -> "All " + artifacts.size() +
                " artifacts successfully downloaded to " + outDir.getPath());

        return resolvedArtifacts;
    }

    private ResolvedArtifact handleResolved(ExecutorService writerExecutor,
                                            ResolvedArtifact resolvedArtifact,
                                            ConcurrentLinkedQueue<String> writeErrors,
                                            File outDir) {

        log.verbosePrintln(() -> resolvedArtifact.artifact + " successfully resolved from " +
                resolvedArtifact.retriever.getDescription());
        writerExecutor.execute(() -> writeArtifact(resolvedArtifact, outDir, writeErrors));
        return resolvedArtifact;
    }

    private void writeArtifact(ResolvedArtifact resolvedArtifact,
                               File outDir,
                               Collection<String> errors) {
        var file = new File(outDir, resolvedArtifact.artifact.toFileName());
        try (var out = new FileOutputStream(file)) {
            try {
                resolvedArtifact.consumeContents(out);
                log.verbosePrintln(() -> "Wrote artifact " + resolvedArtifact.artifact + " to " + file.getPath());
            } catch (IOException e) {
                errors.add("unable to write to file " + file + " due to " + e);
            }
        } catch (IOException e) {
            errors.add("unable to open file " + file + " for writing due to " + e);
        }
    }

    private static String reportErrors(Collection<ArtifactResolution<FileRetrievalError>> fileSystemErrors,
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

    private static Collection<ArtifactResolution<FileRetrievalError>> intersectionOf(
            Collection<ArtifactResolution<FileRetrievalError>> fileSystemErrors,
            Collection<ArtifactResolution<HttpError<byte[]>>> httpErrors) {
        var result = new ArrayList<ArtifactResolution<FileRetrievalError>>(fileSystemErrors.size());
        for (var fsError : fileSystemErrors) {
            for (var httpError : httpErrors) {
                if (fsError.getErrorUnchecked().getArtifact()
                        .equals(httpError.getErrorUnchecked().getArtifact())) {
                    result.add(fsError);
                    break;
                }
            }
        }
        return result;
    }

    private static void reportFileSystemErrors(Collection<ArtifactResolution<FileRetrievalError>> fileSystemErrors,
                                               StringBuilder builder) {
        for (var fileSystemError : fileSystemErrors) {
            var error = fileSystemError.getErrorUnchecked();
            builder.append(error.getArtifact());
            if (error.reason instanceof FileNotFoundException) {
                builder.append(" was not found in ")
                        .append(error.getRetriever().getDescription());
            } else {
                builder.append(" could not be read in ")
                        .append(error.getRetriever().getDescription())
                        .append(" due to ")
                        .append(error.reason);
            }
            builder.append('\n');
        }
    }

    private static void reportHttpErrors(Collection<ArtifactResolution<HttpError<byte[]>>> httpErrors,
                                         StringBuilder builder,
                                         boolean verbose) {
        for (var httpError : httpErrors) {
            var error = httpError.getErrorUnchecked();
            builder.append("ERROR: ")
                    .append(error.getArtifact()).append(" could not be fetched from ")
                    .append(error.getRetriever().getDescription())
                    .append(": http-status=")
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
