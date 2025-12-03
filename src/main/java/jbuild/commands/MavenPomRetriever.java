package jbuild.commands;

import jbuild.api.JBuildException;
import jbuild.artifact.Artifact;
import jbuild.artifact.ResolvedArtifact;
import jbuild.artifact.ResolvedArtifactChecksum;
import jbuild.errors.ArtifactRetrievalError;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.completedStage;
import static java.util.concurrent.CompletableFuture.failedStage;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static jbuild.api.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.commands.FetchCommandExecutor.reportErrors;
import static jbuild.maven.MavenUtils.importsOf;
import static jbuild.util.AsyncUtils.awaitValues;
import static jbuild.util.CollectionUtils.mapEntries;
import static jbuild.util.TextUtils.durationText;

public final class MavenPomRetriever<Err extends ArtifactRetrievalError> {

    private final JBuildLog log;
    private final FetchCommandExecutor<Err> fetchCommandExecutor;
    private final PomCreator pomCreator;
    private final boolean checksum;

    private final Map<Artifact, CompletionStage<Either<MavenPom, NonEmptyCollection<Describable>>>> cache;

    public MavenPomRetriever(JBuildLog log,
                             FetchCommandExecutor<Err> fetchCommandExecutor,
                             PomCreator pomCreator) {
        this(log, fetchCommandExecutor, pomCreator, false);
    }

    public MavenPomRetriever(JBuildLog log,
                             FetchCommandExecutor<Err> fetchCommandExecutor,
                             PomCreator pomCreator,
                             boolean checksum) {
        this.log = log;
        this.fetchCommandExecutor = fetchCommandExecutor;
        this.pomCreator = pomCreator;
        this.cache = new ConcurrentHashMap<>();
        this.checksum = checksum;
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
        var allPoms = artifacts.stream()
                .map(Artifact::pom)
                .collect(toSet());

        log.verbosePrintln(() -> "Will fetch POMs: " + allPoms.stream()
                .map(Artifact::getCoordinates)
                .collect(joining(", ")));

        var fetchCompletions = allPoms.stream()
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
            var pomResult = fetchCommandExecutor.fetchArtifact(a.pom());
            CompletionStage<Either<ResolvedArtifactChecksum, NonEmptyCollection<Describable>>> fullResult;
            if (checksum) {
                fullResult = pomResult.thenComposeAsync(pom ->
                        pom.map(resolvedArtifact -> {
                                    log.verbosePrintln(() -> "Fetching checksum of POM for " +
                                            resolvedArtifact.artifact.getCoordinates());
                                    return fetchCommandExecutor.fetchArtifact(resolvedArtifact.artifact.pom().sha1())
                                            .thenApply(sha -> sha.map(
                                                    shaOk -> ChecksumVerifier.verify(resolvedArtifact, shaOk, log.isVerbose()),
                                                    Either::right));
                                },
                                err -> CompletableFuture.completedStage(Either.right(err))));
            } else {
                fullResult = pomResult.thenApply(e -> e.mapLeft(res -> new ResolvedArtifactChecksum(res, null)));
            }
            return fullResult.thenComposeAsync(res -> res.map(this::handleResolved, this::handleRetrievalErrors));
        });

        if (fromCache.get()) {
            log.verbosePrintln(() -> artifact + " present in cache, will not resolve it again");
        }

        return result;
    }

    private CompletionStage<Either<MavenPom, NonEmptyCollection<Describable>>> handleResolved(
            ResolvedArtifactChecksum resolvedArtifactChecksum) {
        var resolvedArtifact = resolvedArtifactChecksum.artifact;
        var requestDuration = Duration.ofMillis(System.currentTimeMillis() - resolvedArtifact.requestTime);
        log.verbosePrintln(() -> resolvedArtifact.artifact + " successfully resolved (" +
                resolvedArtifact.contentLength + " bytes) from " +
                resolvedArtifact.retriever.getDescription() + " in " + durationText(requestDuration));

        log.verbosePrintln(() -> "Parsing POM of " + resolvedArtifact.artifact);

        return withParentIfNeeded(pomCreator.createPom(resolvedArtifactChecksum));
    }

    private CompletionStage<Either<MavenPom, NonEmptyCollection<Describable>>> handleRetrievalErrors(
            NonEmptyCollection<Describable> errors) {
        log.verbosePrintln(errors.first::getDescription);
        return completedFuture(Either.right(errors));
    }

    private CompletionStage<Either<MavenPom, NonEmptyCollection<Describable>>> withParentIfNeeded(
            CompletionStage<MavenPom> pomCompletion) {
        return pomCompletion.thenComposeAsync(pom -> {
            var parentArtifact = pom.getParentArtifact();

            if (parentArtifact.isEmpty()) {
                return withImportsIfNeeded(pom);
            }

            return fetch(parentArtifact.get().pom()).thenCompose(res -> res.mapLeft(pom::withParent)
                    .map(this::withImportsIfNeeded, err -> completedFuture(Either.right(err))));
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
        default CompletionStage<MavenPom> createPom(ResolvedArtifactChecksum resolvedArtifact) {
            return createPom(resolvedArtifact, true);
        }

        CompletionStage<MavenPom> createPom(ResolvedArtifact resolvedArtifact, boolean consume);

        CompletionStage<MavenPom> createPom(ResolvedArtifactChecksum resolvedArtifact, boolean consume);
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

        @Override
        public CompletionStage<MavenPom> createPom(ResolvedArtifactChecksum resolvedArtifact, boolean consume) {
            return createPom(resolvedArtifact.artifact, consume);
        }
    }

}
