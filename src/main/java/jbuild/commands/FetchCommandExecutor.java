package jbuild.commands;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactResolution;
import jbuild.artifact.ArtifactRetriever;
import jbuild.artifact.ResolvedArtifact;
import jbuild.artifact.file.ArtifactFileWriter;
import jbuild.artifact.file.FileArtifactRetriever;
import jbuild.artifact.http.HttpArtifactRetriever;
import jbuild.errors.ArtifactRetrievalError;
import jbuild.log.JBuildLog;
import jbuild.util.Describable;
import jbuild.util.Either;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static jbuild.commands.FetchCommandExecutor.FetchHandleResult.continueIf;
import static jbuild.util.CollectionUtils.append;
import static jbuild.util.CollectionUtils.foldEither;
import static jbuild.util.CollectionUtils.mapEntries;
import static jbuild.util.CollectionUtils.mapValues;

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

    public <S> Map<Artifact, CompletionStage<Iterable<S>>> fetchArtifacts(Set<? extends Artifact> artifacts,
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
        return completedFuture(List.of());
    }

    private <S> CompletionStage<Iterable<S>> fetch(Artifact artifact,
                                                   ArtifactRetriever<?> retriever,
                                                   Iterator<? extends ArtifactRetriever<?>> remainingRetrievers,
                                                   FetchHandler<S> handler,
                                                   Iterable<S> currentResults) {
        return retriever.retrieve(artifact)
                .thenCompose(resolution -> handler.handle(artifact, resolution)
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
            return completedFuture(results);
        }
    }

    public Map<Artifact, CompletionStage<Optional<ResolvedArtifact>>> fetchArtifacts(
            Set<? extends Artifact> artifacts,
            ArtifactFileWriter fileWriter) {
        // first stage: run all retrievers and output handlers, accumulating the results for each artifact
        var fetchCompletions = fetchArtifacts(
                artifacts,
                (requestedArtifact, resolution) -> resolution.value.map(
                        success -> handleResolved(fileWriter, success),
                        this::handleRetrievalError
                ).thenApply(res ->
                        continueIf(res.map(ok -> false, err -> err instanceof ArtifactRetrievalError),
                                res, resolution)));

        // second stage: check that for each artifact, at least one retrieval was fully successful,
        // otherwise group the errors
        Map<Artifact, CompletionStage<Either<ResolvedArtifact, List<Describable>>>> errorFoldingCompletions =
                mapValues(fetchCompletions, c -> c.thenApply((resolutions) ->
                        foldEither(resolutions, fetchCompletions.size())));

        // third stage: report all successes if verbose log is enabled
        Map<Artifact, CompletionStage<Either<ResolvedArtifact, List<Describable>>>> reportingCompletions;

        if (log.isVerbose()) {
            reportingCompletions = mapValues(errorFoldingCompletions, c -> c.thenApply((result) -> result.map(
                    this::reportSuccess,
                    Either::right
            )));
        } else {
            reportingCompletions = errorFoldingCompletions;
        }

        // final stage: report all errors
        return mapEntries(reportingCompletions, (artifact, c) -> c.thenApply((result) -> result.map(
                Optional::of,
                errors -> reportErrors(log, artifact, errors)
        )));
    }

    private <T> Either<ResolvedArtifact, T> reportSuccess(ResolvedArtifact resolvedArtifact) {
        log.verbosePrintln(() -> "Successfully retrieved " + resolvedArtifact.artifact +
                " (" + resolvedArtifact.contentLength + " bytes)");
        return Either.left(resolvedArtifact);
    }

    private CompletionStage<Either<ResolvedArtifact, Describable>> handleResolved(
            ArtifactFileWriter fileWriter,
            ResolvedArtifact resolvedArtifact) {
        log.verbosePrintln(() -> resolvedArtifact.artifact + " successfully resolved from " +
                resolvedArtifact.retriever.getDescription());
        return fileWriter.write(resolvedArtifact).thenApply(result -> result.map(file -> {
            log.verbosePrintln(() -> "Wrote artifact " + resolvedArtifact.artifact + " to " + file.getPath());
            return Either.left(resolvedArtifact);
        }, Either::right));
    }

    private CompletionStage<Either<ResolvedArtifact, Describable>> handleRetrievalError(
            ArtifactRetrievalError error) {
        log.verbosePrintln(error::getDescription);
        return completedFuture(Either.right(error));
    }

    static <T> Optional<T> reportErrors(
            JBuildLog log,
            Artifact artifact,
            List<? extends Describable> errors) {
        var builder = new StringBuilder(4096);
        if (!errors.isEmpty()) {
            builder.append("Unable to retrieve ").append(artifact).append(" due to:\n");
            for (var error : errors) {
                builder.append("  * ");
                error.describe(builder, log.isVerbose());
                builder.append('\n');
            }
        } else {
            builder.append("Unable to retrieve ").append(artifact).append('\n');
        }
        log.print(builder);
        return Optional.empty();
    }

    public interface FetchHandleResult<Res> {

        boolean shouldContinue();

        Res getResult();

        ArtifactResolution<?> getResolution();

        static <Res> FetchHandleResult<Res> continueIf(boolean condition,
                                                       Res result,
                                                       ArtifactResolution<?> resolution) {
            return new FetchHandleResult<Res>() {
                @Override
                public boolean shouldContinue() {
                    return condition;
                }

                @Override
                public Res getResult() {
                    return result;
                }

                @Override
                public ArtifactResolution<?> getResolution() {
                    return resolution;
                }
            };
        }
    }

    public interface FetchHandler<Res> {
        CompletionStage<FetchHandleResult<Res>> handle(Artifact requestedArtifact,
                                                       ArtifactResolution<?> resolution);
    }
}
