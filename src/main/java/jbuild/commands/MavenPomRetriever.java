package jbuild.commands;

import jbuild.artifact.Artifact;
import jbuild.artifact.ResolvedArtifact;
import jbuild.errors.ArtifactRetrievalError;
import jbuild.errors.JBuildException;
import jbuild.log.JBuildLog;
import jbuild.maven.MavenPom;
import jbuild.maven.MavenUtils;
import jbuild.util.Describable;
import jbuild.util.Either;
import jbuild.util.NonEmptyCollection;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.completedStage;
import static java.util.concurrent.CompletableFuture.failedStage;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static jbuild.commands.FetchCommandExecutor.reportErrors;
import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.maven.MavenUtils.importsOf;
import static jbuild.util.AsyncUtils.awaitValues;
import static jbuild.util.CollectionUtils.mapEntries;
import static jbuild.util.Either.awaitLeft;
import static jbuild.util.TextUtils.durationText;

public final class MavenPomRetriever<Err extends ArtifactRetrievalError> {

    private final JBuildLog log;
    private final FetchCommandExecutor<Err> fetchCommandExecutor;
    private final PomCreator pomCreator;

    private final Map<Artifact, CompletionStage<Either<MavenPom, NonEmptyCollection<Describable>>>> cache;

    public MavenPomRetriever(JBuildLog log,
                             FetchCommandExecutor<Err> fetchCommandExecutor,
                             PomCreator pomCreator) {
        this.log = log;
        this.fetchCommandExecutor = fetchCommandExecutor;
        this.pomCreator = pomCreator;
        this.cache = new ConcurrentHashMap<>();
    }

    public static MavenPomRetriever<? extends ArtifactRetrievalError> createDefault(JBuildLog log) {
        return createDefault(log, DefaultPomCreator.INSTANCE);
    }

    public static MavenPomRetriever<? extends ArtifactRetrievalError> createDefault(JBuildLog log,
                                                                                    PomCreator pomCreator) {
        return new MavenPomRetriever<>(log, FetchCommandExecutor.createDefault(log), pomCreator);
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

        return withParentIfNeeded(pomCreator.createPom(resolvedArtifact));
    }

    private CompletionStage<Either<MavenPom, NonEmptyCollection<Describable>>> handleRetrievalErrors(
            NonEmptyCollection<Describable> errors) {
        log.verbosePrintln(errors.first::getDescription);
        return completedFuture(Either.right(errors));
    }

    private CompletionStage<Either<MavenPom, NonEmptyCollection<Describable>>> withParentIfNeeded(
            CompletionStage<MavenPom> pomCompletion) {
        return pomCompletion.thenCompose(pom -> {
            var parentArtifact = pom.getParentArtifact();

            if (parentArtifact.isEmpty()) {
                return withImportsIfNeeded(pom);
            }

            return withImportsIfNeeded(pom).thenComposeAsync(imps -> awaitLeft(imps, withImps -> {
                var parentPom = parentArtifact.get().pom();

                log.verbosePrintln(() -> "Fetching parent POM of " + withImps.getArtifact().getCoordinates() +
                        " - " + parentPom.getCoordinates());

                return fetch(parentPom).thenApply(res -> res.mapLeft(withImps::withParent));
            }));
        });
    }

    private CompletionStage<Either<MavenPom, NonEmptyCollection<Describable>>> withImportsIfNeeded(MavenPom pom) {
        var imports = importsOf(pom);

        if (imports.isEmpty()) {
            return completedFuture(Either.left(pom));
        }

        log.verbosePrintln(() -> "Importing artifacts into " + pom.getArtifact().getCoordinates() +
                " - " + imports.stream()
                .map(Artifact::getCoordinates)
                .collect(joining(", ")));

        return awaitValues(fetchPoms(imports)).thenApply(res -> {
            var errors = new ArrayList<Describable>(imports.size() / 2);
            var resultPom = pom;
            for (var item : res.values()) {
                MavenPom imp = item.map(p -> p.orElseGet(() -> {
                    errors.add(Describable.of("Maven import from " +
                            pom.getArtifact().getCoordinates() + " could not be resolved"));
                    return null;
                }), err -> {
                    errors.add(Describable.of("Error fetching Maven import from " +
                            pom.getArtifact().getCoordinates() + " - " + err));
                    return null;
                });
                if (imp != null) {
                    resultPom = resultPom.importing(imp);
                }
            }
            if (errors.isEmpty()) {
                return Either.left(resultPom);
            }
            return Either.right(NonEmptyCollection.of(errors));
        });
    }

    public interface PomCreator {
        default CompletionStage<MavenPom> createPom(ResolvedArtifact resolvedArtifact) {
            return createPom(resolvedArtifact, true);
        }

        CompletionStage<MavenPom> createPom(ResolvedArtifact resolvedArtifact, boolean consume);
    }

    public enum DefaultPomCreator implements PomCreator {
        INSTANCE;

        @Override
        public CompletionStage<MavenPom> createPom(ResolvedArtifact artifact, boolean consume) {
            var contents = consume ? artifact.consumeContents() : new ByteArrayInputStream(artifact.getContents());
            try {
                return completedStage(MavenUtils.parsePom(contents));
            } catch (ParserConfigurationException | IOException | SAXException e) {
                return failedStage(new JBuildException("Could not parse POM of '" +
                        artifact.artifact.getCoordinates() + "' due to: " + e, ACTION_ERROR));
            }

        }
    }

}
