package jbuild.commands;

import jbuild.artifact.Artifact;
import jbuild.artifact.ResolvedArtifact;
import jbuild.errors.ArtifactRetrievalError;
import jbuild.log.JBuildLog;
import jbuild.maven.MavenPom;
import jbuild.maven.MavenUtils;
import jbuild.util.CollectionUtils;
import jbuild.util.Describable;
import jbuild.util.Either;
import jbuild.util.NonEmptyCollection;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toSet;
import static jbuild.commands.FetchCommandExecutor.FetchHandleResult.continueIf;
import static jbuild.commands.FetchCommandExecutor.reportErrors;
import static jbuild.util.CollectionUtils.mapEntries;
import static jbuild.util.CollectionUtils.mapValues;

public final class DepsCommandExecutor<Err extends ArtifactRetrievalError> {

    private final JBuildLog log;
    private final FetchCommandExecutor<Err> fetchCommandExecutor;

    public DepsCommandExecutor(JBuildLog log,
                               FetchCommandExecutor<Err> fetchCommandExecutor) {
        this.log = log;
        this.fetchCommandExecutor = fetchCommandExecutor;
    }

    public static DepsCommandExecutor<? extends ArtifactRetrievalError> createDefault(JBuildLog log) {
        return new DepsCommandExecutor<>(log, FetchCommandExecutor.createDefault(log));
    }

    public Map<Artifact, CompletionStage<Optional<MavenPom>>> fetchPoms(
            Set<? extends Artifact> artifacts) {
        // first stage: run all retrievers and parse POMs, accumulating the results for each artifact
        var fetchCompletions = fetchCommandExecutor.fetchArtifacts(
                artifacts.stream()
                        .map(Artifact::pom)
                        .collect(toSet()),
                (requestedArtifact, resolution) -> resolution.value.map(
                        this::handleResolved,
                        this::handleRetrievalError
                ).thenApply(res -> continueIf(res.map(ok -> false, err -> true), res)));

        // second stage: check that for each artifact, at least one retrieval was fully successful,
        // otherwise group the errors
        Map<Artifact, CompletionStage<Either<MavenPom, NonEmptyCollection<Describable>>>> errorCheckCompletions =
                mapValues(fetchCompletions, completion ->
                        completion.thenApply(CollectionUtils::foldEither));

        // final stage: report all errors
        return mapEntries(errorCheckCompletions, (artifact, c) -> c.thenApply((result) -> result.map(
                Optional::of,
                errors -> reportErrors(log, artifact, errors)
        )));
    }

    private CompletionStage<Either<MavenPom, NonEmptyCollection<Describable>>> handleResolved(
            ResolvedArtifact resolvedArtifact) {
        log.verbosePrintln(() -> resolvedArtifact.artifact + " successfully resolved from " +
                resolvedArtifact.retriever.getDescription());

        log.verbosePrintln(() -> "Parsing POM of " + resolvedArtifact.artifact);

        try {
            return withParentIfNeeded(MavenUtils.parsePom(resolvedArtifact.consumeContents()));
        } catch (ParserConfigurationException | IOException | SAXException e) {
            return CompletableFuture.completedFuture(Either.right(
                    NonEmptyCollection.of(Describable.of("Unable to parse POM of " +
                            resolvedArtifact.artifact + " due to: " + e))));
        }
    }

    private CompletionStage<Either<MavenPom, NonEmptyCollection<Describable>>> handleRetrievalError(
            Describable error) {
        log.verbosePrintln(error::getDescription);
        return completedFuture(Either.right(NonEmptyCollection.of(error)));
    }

    private CompletionStage<Either<MavenPom, NonEmptyCollection<Describable>>> handleRetrievalErrors(
            NonEmptyCollection<Describable> errors) {
        log.verbosePrintln(errors.first::getDescription);
        return completedFuture(Either.right(errors));
    }

    private CompletionStage<Either<MavenPom, NonEmptyCollection<Describable>>> withParentIfNeeded(MavenPom pom) {
        var parentArtifact = pom.getParent();
        if (parentArtifact.isEmpty()) {
            return completedFuture(Either.left(pom));
        }
        // TODO combine pom with the retrieved parent pom
        return fetchCommandExecutor.fetchArtifact(parentArtifact.get())
                .thenComposeAsync(res -> res.map(this::handleResolved, this::handleRetrievalErrors));
    }

}
