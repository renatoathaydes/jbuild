package jbuild.commands;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactMetadata;
import jbuild.artifact.ArtifactRetriever;
import jbuild.artifact.DefaultArtifactRetrievers;
import jbuild.log.JBuildLog;
import jbuild.util.Describable;
import jbuild.util.Either;
import jbuild.util.NonEmptyCollection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.stream.Collectors.toList;

public final class VersionsCommandExecutor {

    private final JBuildLog log;
    private final List<? extends ArtifactRetriever<?>> retrievers;

    public VersionsCommandExecutor(JBuildLog log,
                                   NonEmptyCollection<? extends ArtifactRetriever<?>> retrievers) {
        this.log = log;
        this.retrievers = retrievers.stream().collect(toList());
    }

    public VersionsCommandExecutor(JBuildLog log) {
        this(log, DefaultArtifactRetrievers.get());
    }

    public Map<Artifact, CompletionStage<Either<ArtifactMetadata, NonEmptyCollection<Describable>>>> getVersions(
            Set<? extends Artifact> artifacts) {
        var results = new HashMap<
                Artifact,
                CompletionStage<Either<ArtifactMetadata, NonEmptyCollection<Describable>>>>(artifacts.size());

        for (var artifact : artifacts) {
            results.put(artifact, fetch(artifact));
        }

        return results;
    }

    private CompletionStage<Either<ArtifactMetadata, NonEmptyCollection<Describable>>> fetch(Artifact artifact) {
        var counter = new AtomicInteger(retrievers.size());
        var successes = new ConcurrentLinkedQueue<ArtifactMetadata>();
        var errors = new ConcurrentLinkedQueue<Describable>();

        var result = new CompletableFuture<Either<ArtifactMetadata, NonEmptyCollection<Describable>>>();

        for (var retriever : retrievers) {
            log.verbosePrintln(() -> "Requesting metadata for " + artifact.getCoordinates() + " at " +
                    retriever.getDescription());
            var requestTime = System.currentTimeMillis();

            retriever.retrieveMetadata(artifact).whenComplete((completion, err) -> {
                log.verbosePrintln(() -> artifact.getCoordinates() + " metadata request completed in " +
                        (System.currentTimeMillis() - requestTime) + " ms");
                var count = counter.decrementAndGet();
                try {
                    if (err == null) {
                        completion.use(successes::add, errors::add);
                    } else {
                        log.verbosePrintln(() -> "Problem fetching metadata for " +
                                artifact.getCoordinates() + ": " + err);

                        errors.add((builder, verbose) ->
                                builder.append(retriever.getDescription())
                                        .append(" could not fetch versions for ")
                                        .append(artifact.getCoordinates())
                                        .append(" due to ")
                                        .append(err));
                    }
                } catch (Exception e) {
                    errors.add((builder, verbose) -> builder.append("Unexpected error while fetching metadata for ")
                            .append(artifact.getCoordinates()).append(": ").append(e));
                } finally {
                    if (count == 0) {
                        if (successes.isEmpty()) {
                            result.complete(Either.right(NonEmptyCollection.of(errors)));
                        } else {
                            result.complete(Either.left(mergeMetadata(NonEmptyCollection.of(successes))));
                        }
                    }
                }
            });
        }

        return result;
    }

    private ArtifactMetadata mergeMetadata(NonEmptyCollection<ArtifactMetadata> metadatas) {
        var iter = metadatas.iterator();
        var result = iter.next();
        while (iter.hasNext()) {
            result = result.merge(iter.next());
        }
        return result;
    }

}
