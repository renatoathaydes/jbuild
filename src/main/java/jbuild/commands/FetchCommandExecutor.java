package jbuild.commands;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactResolution;
import jbuild.artifact.ArtifactRetriever;
import jbuild.artifact.DefaultArtifactRetrievers;
import jbuild.artifact.ResolvedArtifact;
import jbuild.artifact.file.ArtifactFileWriter;
import jbuild.errors.ArtifactRetrievalError;
import jbuild.log.JBuildLog;
import jbuild.util.CollectionUtils;
import jbuild.util.Describable;
import jbuild.util.Either;
import jbuild.util.NonEmptyCollection;
import jbuild.util.TextUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static jbuild.commands.FetchCommandExecutor.FetchHandleResult.continueIf;
import static jbuild.util.CollectionUtils.mapEntries;
import static jbuild.util.CollectionUtils.mapValues;
import static jbuild.util.TextUtils.durationText;

public final class FetchCommandExecutor<Err extends ArtifactRetrievalError> {

    private final JBuildLog log;
    private final NonEmptyCollection<? extends ArtifactRetriever<? extends Err>> retrievers;

    public FetchCommandExecutor(JBuildLog log,
                                NonEmptyCollection<? extends ArtifactRetriever<? extends Err>> retrievers) {
        this.log = log;
        this.retrievers = retrievers;
    }

    public static FetchCommandExecutor<ArtifactRetrievalError> createDefault(JBuildLog log) {
        return new FetchCommandExecutor<>(log, DefaultArtifactRetrievers.get());
    }

    public CompletionStage<Either<ResolvedArtifact, NonEmptyCollection<Describable>>> fetchArtifact(Artifact artifact) {
        return fetchArtifact(artifact,
                (FetchHandler<Either<ResolvedArtifact, NonEmptyCollection<Describable>>>)
                        (requestedArtifact, resolution) -> completedFuture(
                                continueIf(resolution.value.map(ok -> false, err -> true),
                                        resolution.value.map(
                                                Either::left,
                                                err -> Either.right(NonEmptyCollection.of(err)))))
        ).thenApply(CollectionUtils::foldEither);
    }

    public <S> Map<Artifact, CompletionStage<NonEmptyCollection<S>>> fetchArtifacts(Set<? extends Artifact> artifacts,
                                                                                    FetchHandler<S> handler) {
        var result = new HashMap<Artifact, CompletionStage<NonEmptyCollection<S>>>(artifacts.size());
        for (var artifact : artifacts) {
            var completion = fetchArtifact(artifact, handler);
            result.put(artifact, completion);
        }
        return result;
    }

    public <S> CompletionStage<NonEmptyCollection<S>> fetchArtifact(
            Artifact artifact,
            FetchHandler<S> handler) {
        var retrievers = this.retrievers.iterator();
        return fetch(artifact, retrievers.next(), retrievers, handler, List.of());
    }

    private <S> CompletionStage<NonEmptyCollection<S>> fetch(Artifact artifact,
                                                             ArtifactRetriever<?> retriever,
                                                             Iterator<? extends ArtifactRetriever<?>> remainingRetrievers,
                                                             FetchHandler<S> handler,
                                                             Iterable<S> currentResults) {
        return retriever.retrieve(artifact)
                .thenCompose(resolution -> handler.handle(artifact, resolution)
                        .thenCompose(res ->
                                fetchIfNotDone(artifact, remainingRetrievers, handler, currentResults, res)));
    }

    private <S> CompletionStage<NonEmptyCollection<S>> fetchIfNotDone(Artifact artifact,
                                                                      Iterator<? extends ArtifactRetriever<?>> remainingRetrievers,
                                                                      FetchHandler<S> handler,
                                                                      Iterable<S> currentResults,
                                                                      FetchHandleResult<S> result) {
        var results = NonEmptyCollection.of(currentResults, result.getResult());
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
                ).thenApply(res -> continueIf(res.map(ok -> false, err -> true), res)));

        // second stage: check that for each artifact, at least one retrieval was fully successful,
        // otherwise group the errors
        Map<Artifact, CompletionStage<Either<ResolvedArtifact, NonEmptyCollection<Describable>>>> errorFoldingCompletions =
                mapValues(fetchCompletions, c ->
                        c.thenApply(CollectionUtils::foldEither));

        // third stage: report all successes if verbose log is enabled
        Map<Artifact, CompletionStage<Either<ResolvedArtifact, NonEmptyCollection<Describable>>>> reportingCompletions;

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

    private CompletionStage<Either<ResolvedArtifact, NonEmptyCollection<Describable>>> handleResolved(
            ArtifactFileWriter fileWriter,
            ResolvedArtifact resolvedArtifact) {
        var requestDuration = Duration.ofMillis(System.currentTimeMillis() - resolvedArtifact.requestTime);
        log.verbosePrintln(() -> resolvedArtifact.artifact + " successfully resolved from " +
                resolvedArtifact.retriever.getDescription() + " in " + durationText(requestDuration));
        return fileWriter.write(resolvedArtifact, true).thenApply(result -> result.map(file -> {
            log.verbosePrintln(() -> "Wrote artifact " + resolvedArtifact.artifact + " to " + file.getPath());
            return Either.left(resolvedArtifact);
        }, err -> Either.right(NonEmptyCollection.of(err))));
    }

    private CompletionStage<Either<ResolvedArtifact, NonEmptyCollection<Describable>>> handleRetrievalError(
            ArtifactRetrievalError error) {
        log.verbosePrintln(error::getDescription);
        return completedFuture(Either.right(NonEmptyCollection.of(error)));
    }

    static <T> Optional<T> reportErrors(
            JBuildLog log,
            Artifact artifact,
            NonEmptyCollection<? extends Describable> errors) {
        var builder = new StringBuilder(4096);
        builder.append("Unable to retrieve ").append(artifact).append(" due to:").append(TextUtils.LINE_END);
        for (var error : errors) {
            builder.append("  * ");
            error.describe(builder, log.isVerbose());
            builder.append(TextUtils.LINE_END);
        }
        log.print(builder);
        return Optional.empty();
    }

    public interface FetchHandleResult<Res> {

        boolean shouldContinue();

        Res getResult();

        static <Res> FetchHandleResult<Res> continueIf(boolean condition,
                                                       Res result) {
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
        CompletionStage<FetchHandleResult<Res>> handle(Artifact requestedArtifact,
                                                       ArtifactResolution<?> resolution);
    }
}
