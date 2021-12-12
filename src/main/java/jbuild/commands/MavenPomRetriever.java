package jbuild.commands;

import jbuild.artifact.Artifact;
import jbuild.artifact.ResolvedArtifact;
import jbuild.errors.ArtifactRetrievalError;
import jbuild.log.JBuildLog;
import jbuild.maven.MavenPom;
import jbuild.maven.MavenUtils;
import jbuild.util.Describable;
import jbuild.util.Either;
import jbuild.util.NonEmptyCollection;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static jbuild.commands.FetchCommandExecutor.reportErrors;
import static jbuild.util.CollectionUtils.mapEntries;
import static jbuild.util.TextUtils.durationText;

public final class MavenPomRetriever<Err extends ArtifactRetrievalError> {

    private final JBuildLog log;
    private final FetchCommandExecutor<Err> fetchCommandExecutor;

    private final Map<Artifact, CompletionStage<Either<MavenPom, NonEmptyCollection<Describable>>>> cache;

    public MavenPomRetriever(JBuildLog log,
                             FetchCommandExecutor<Err> fetchCommandExecutor) {
        this.log = log;
        this.fetchCommandExecutor = fetchCommandExecutor;
        this.cache = new ConcurrentHashMap<>();
    }

    public static MavenPomRetriever<? extends ArtifactRetrievalError> createDefault(JBuildLog log) {
        return new MavenPomRetriever<>(log, FetchCommandExecutor.createDefault(log));
    }

    public Map<Artifact, CompletionStage<Optional<MavenPom>>> fetchPoms(
            Set<? extends Artifact> artifacts) {
        // first stage: run all retrievers and parse POMs, accumulating the results for each artifact
        var fetchCompletions = artifacts.stream()
                .map(Artifact::pom)
                .collect(toSet()).stream()
                .collect(toMap(a -> a, this::fetch));

        // final stage: report all errors
        return mapEntries(fetchCompletions, (artifact, c) -> c.thenApply((result) -> result.map(
                Optional::of,
                errors -> reportErrors(log, artifact, errors)
        )));
    }

    public CompletionStage<Optional<MavenPom>> fetchPom(Artifact artifact) {
        return fetch(artifact).thenApply(pom -> pom.map(
                Optional::of,
                errors -> reportErrors(log, artifact, errors)));
    }

    private CompletionStage<Either<MavenPom, NonEmptyCollection<Describable>>> fetch(Artifact artifact) {
        var fromCache = new AtomicBoolean(true);
        var result = cache.computeIfAbsent(artifact, a -> {
            fromCache.set(false);
            return fetchCommandExecutor.fetchArtifact(a.pom())
                    .thenComposeAsync(res -> res.map(this::handleResolved, this::handleRetrievalErrors));
        });

        if (fromCache.get()) {
            log.verbosePrintln(() -> artifact + " present in cache, will not resolve it again");
        }

        return result;
    }

    private CompletionStage<Either<MavenPom, NonEmptyCollection<Describable>>> handleResolved(
            ResolvedArtifact resolvedArtifact) {
        var requestDuration = Duration.ofMillis(System.currentTimeMillis() - resolvedArtifact.requestTime);
        log.verbosePrintln(() -> resolvedArtifact.artifact + " successfully resolved (" +
                resolvedArtifact.contentLength + " bytes) from " +
                resolvedArtifact.retriever.getDescription() + " in " + durationText(requestDuration));

        log.verbosePrintln(() -> "Parsing POM of " + resolvedArtifact.artifact);

        try {
            return withParentIfNeeded(MavenUtils.parsePom(resolvedArtifact.consumeContents()));
        } catch (ParserConfigurationException | IOException | SAXException e) {
            return CompletableFuture.completedFuture(Either.right(
                    NonEmptyCollection.of(Describable.of("Unable to parse POM of " +
                            resolvedArtifact.artifact + " due to: " + e))));
        }
    }

    private CompletionStage<Either<MavenPom, NonEmptyCollection<Describable>>> handleRetrievalErrors(
            NonEmptyCollection<Describable> errors) {
        log.verbosePrintln(errors.first::getDescription);
        return completedFuture(Either.right(errors));
    }

    private CompletionStage<Either<MavenPom, NonEmptyCollection<Describable>>> withParentIfNeeded(MavenPom pom) {
        var parentArtifact = pom.getParentArtifact();
        if (parentArtifact.isEmpty()) {
            return completedFuture(Either.left(pom));
        }
        return fetch(parentArtifact.get().pom())
                .thenComposeAsync(res -> res.map(
                        parentPom -> completedFuture(Either.left(pom.withParent(parentPom))),
                        this::handleRetrievalErrors));
    }

}
