package jbuild.commands;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactResolution;
import jbuild.artifact.ArtifactRetriever;
import jbuild.artifact.ResolvedArtifact;
import jbuild.artifact.file.FileArtifactRetriever;
import jbuild.artifact.http.HttpArtifactRetriever;
import jbuild.errors.ArtifactRetrievalError;
import jbuild.errors.JBuildException;
import jbuild.log.JBuildLog;
import jbuild.util.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static jbuild.commands.FetchCommandExecutor.FetchHandleResult.continueIf;
import static jbuild.errors.JBuildException.ErrorCause.IO_WRITE;
import static jbuild.errors.JBuildException.ErrorCause.TIMEOUT;
import static jbuild.util.CollectionUtils.append;

public final class FetchCommandExecutor<Err extends ArtifactRetrievalError> {

    private final JBuildLog log;
    private final List<? extends ArtifactRetriever<? extends Err>> retrievers;

    public FetchCommandExecutor(JBuildLog log,
                                List<? extends ArtifactRetriever<? extends Err>> retrievers) {
        this.log = log;
        this.retrievers = retrievers;
    }

    public static FetchCommandExecutor<? extends ArtifactRetrievalError> createDefault(JBuildLog log) {
        return new FetchCommandExecutor<>(log, List.of(
                new FileArtifactRetriever(),
                new HttpArtifactRetriever()));
    }

    public <S> Map<Artifact, CompletionStage<Iterable<S>>> fetchArtifacts(List<? extends Artifact> artifacts,
                                                                          FetchHandler<S> handler) {
        var result = new HashMap<Artifact, CompletionStage<Iterable<S>>>(artifacts.size());
        for (Artifact artifact : artifacts) {
            var completion = fetch(artifact, retrievers.iterator(), handler);
            result.put(artifact, completion);
        }
        return result;
    }

    private <S> CompletionStage<Iterable<S>> fetch(
            Artifact artifact,
            Iterator<? extends ArtifactRetriever<?>> remainingRetrievers,
            FetchHandler<S> handler) {
        if (remainingRetrievers.hasNext()) {
            var retriever = remainingRetrievers.next();
            return fetch(artifact, retriever, remainingRetrievers, handler, List.of());
        }
        return CompletableFuture.completedFuture(List.of());
    }

    private <S> CompletionStage<Iterable<S>> fetch(Artifact artifact,
                                                   ArtifactRetriever<?> retriever,
                                                   Iterator<? extends ArtifactRetriever<?>> remainingRetrievers,
                                                   FetchHandler<S> handler,
                                                   Iterable<S> currentResults) {
        return retriever.retrieve(artifact)
                .thenCompose(resolution -> handler.handle(resolution)
                        .thenCompose(res ->
                                fetchIfNotDone(artifact, remainingRetrievers, handler, currentResults, res)));
    }

    private <S> CompletionStage<Iterable<S>> fetchIfNotDone(Artifact artifact,
                                                            Iterator<? extends ArtifactRetriever<?>> remainingRetrievers,
                                                            FetchHandler<S> handler,
                                                            Iterable<S> currentResults,
                                                            FetchHandleResult<S> result) {
        var results = append(currentResults, result.getResult());
        if (remainingRetrievers.hasNext() && result.shouldContinue()) {
            return fetch(artifact, remainingRetrievers.next(), remainingRetrievers, handler, results);
        } else {
            return CompletableFuture.completedFuture(results);
        }
    }

    public List<ResolvedArtifact> fetchArtifacts(List<Artifact> artifacts, File outDir) {
        var dirExists = FileUtils.ensureDirectoryExists(outDir);
        if (!dirExists) {
            throw new JBuildException(
                    "Output directory does not exist and cannot be created: " + outDir.getPath(),
                    IO_WRITE);
        }

        var writerExecutor = Executors.newSingleThreadExecutor((runnable) -> {
            var thread = new Thread(runnable, "command-executor-output-writer");
            thread.setDaemon(true);
            return thread;
        });

        // no concurrency needed because we only write in one thread
        var writeErrors = new ArrayList<String>();
        var resolvedArtifacts = new ArrayList<ResolvedArtifact>();
        var retrievalErrors = new HashMap<Artifact, List<ArtifactRetrievalError>>();

        var allCompletions = fetchArtifacts(artifacts, resolution -> {
            var error = resolution.with(
                    success -> handleResolved(writerExecutor, success, writeErrors, outDir),
                    this::handleFailure);
            return CompletableFuture.completedFuture(continueIf(error != null, resolution));
        });

        var completedArtifactsLatch = new CountDownLatch(artifacts.size());

        allCompletions.forEach((artifact, result) -> result.thenAccept((resolutions) -> {
            writerExecutor.submit(() -> {
                for (var resolution : resolutions) {
                    resolution.use(resolvedArtifacts::add,
                            error -> retrievalErrors.computeIfAbsent(artifact,
                                    (ignore) -> new ArrayList<>(4)
                            ).add(error));
                }
            });
            completedArtifactsLatch.countDown();
        }));

        // wait for all artifact resolution completions, then let the writer executor finish its job

        try {
            var ok = completedArtifactsLatch.await(300, TimeUnit.SECONDS);
            if (!ok) {
                writerExecutor.shutdownNow();
                throw new JBuildException("Timeout waiting for all artifacts to get fetched.\n" +
                        "To increase the timeout, run jbuild with --timeout <seconds>", TIMEOUT);
            }
        } catch (InterruptedException e) {
            writerExecutor.shutdownNow();
            throw new RuntimeException(e);
        }

        // any artifacts that got resolved successfully should be removed from the list of retrievalErrors
        writerExecutor.submit(() -> {
            for (var resolved : resolvedArtifacts) {
                retrievalErrors.remove(resolved.artifact);
            }
        });

        writerExecutor.shutdown();

        try {
            var ok = writerExecutor.awaitTermination(30, TimeUnit.SECONDS);
            if (!ok) {
                throw new JBuildException("Could not terminate writing artifacts within timeout", TIMEOUT);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (!retrievalErrors.isEmpty() || !writeErrors.isEmpty()) {
            throw new JBuildException(reportErrors(retrievalErrors, writeErrors, log.isVerbose()), IO_WRITE);
        }

        log.verbosePrintln(() -> "All " + artifacts.size() +
                " artifacts successfully downloaded to " + outDir.getPath());

        return resolvedArtifacts;
    }

    private <T> T handleResolved(ExecutorService writerExecutor,
                                 ResolvedArtifact resolvedArtifact,
                                 Collection<String> writeErrors,
                                 File outDir) {
        log.verbosePrintln(() -> resolvedArtifact.artifact + " successfully resolved from " +
                resolvedArtifact.retriever.getDescription());
        writerExecutor.execute(() -> writeArtifact(resolvedArtifact, outDir, writeErrors));
        return null;
    }

    private ArtifactRetrievalError handleFailure(ArtifactRetrievalError error) {
        log.verbosePrintln(error::getDescription);
        return error;
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

    private static String reportErrors(Map<Artifact, List<ArtifactRetrievalError>> retrievalErrors,
                                       Collection<String> writeErrors,
                                       boolean verbose) {
        var builder = new StringBuilder(4096);
        if (!retrievalErrors.isEmpty()) {
            builder.append("Artifact retrieval errors:\n");
            retrievalErrors.forEach((artifact, artifactRetrievalErrors) -> {
                for (var error : artifactRetrievalErrors) {
                    builder.append("  * ");
                    error.describe(builder, verbose);
                    builder.append('\n');
                }
            });
        }
        if (!writeErrors.isEmpty()) {
            builder.append("Artifact writing errors:\n");
            for (var error : writeErrors) {
                builder.append("  * ").append(error).append('\n');
            }
        }
        return builder.toString();
    }

    public interface FetchHandleResult<Res> {
        boolean shouldContinue();

        Res getResult();

        static <Res> FetchHandleResult<Res> continueIf(boolean condition, Res result) {
            return new FetchHandleResult<Res>() {
                @Override
                public boolean shouldContinue() {
                    return condition;
                }

                @Override
                public Res getResult() {
                    return result;
                }
            };
        }
    }

    public interface FetchHandler<Res> {
        CompletionStage<FetchHandleResult<Res>> handle(ArtifactResolution<?> resolution);
    }
}
