package jbuild.commands;

import jbuild.artifact.Artifact;
import jbuild.artifact.ResolvedArtifact;
import jbuild.artifact.file.ArtifactFileWriter;
import jbuild.artifact.file.MultiArtifactFileWriter;
import jbuild.log.JBuildLog;
import jbuild.maven.DependencyExclusions;
import jbuild.maven.DependencyTree;
import jbuild.maven.ResolvedDependency;
import jbuild.maven.Scope;
import jbuild.util.Describable;
import jbuild.util.Either;
import jbuild.util.NonEmptyCollection;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.completedStage;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static jbuild.maven.MavenUtils.extensionOfPackaging;
import static jbuild.util.AsyncUtils.awaitValues;
import static jbuild.util.CollectionUtils.foldEither;

public final class InstallCommandExecutor {

    public static final String LIBS_DIR = "java-libs";

    private final JBuildLog log;
    private final FetchCommandExecutor<?> fetchCommand;
    private final ArtifactFileWriter writer;

    public InstallCommandExecutor(JBuildLog log,
                                  FetchCommandExecutor<?> fetchCommand,
                                  ArtifactFileWriter writer) {
        this.log = log;
        this.fetchCommand = fetchCommand;
        this.writer = writer;
    }

    public static InstallCommandExecutor create(JBuildLog log,
                                                ArtifactFileWriter writer) {

        return new InstallCommandExecutor(log, FetchCommandExecutor.createDefault(log), writer);
    }

    public CompletionStage<Either<Long, NonEmptyCollection<Throwable>>> installDependencyTree(
            Set<? extends Artifact> artifacts,
            EnumSet<Scope> scopes,
            boolean optional,
            boolean transitive,
            DependencyExclusions exclusions,
            boolean checksum) {
        var depsCommand = new DepsCommandExecutor<>(log, new MavenPomRetriever<>(log, fetchCommand, writer, checksum));

        if (!transitive) {
            // not installing transitive dependencies, but may still need to retrieve poms for the repository writer
            var mustInstallPom = includesMavenRepositoryWriter(writer);
            return awaitValues(artifacts.stream().map(artifact ->
                            install(DependencyTree.childless(artifact, null), checksum, mustInstallPom))
                    .collect(toList()))
                    .thenApply(this::groupErrors)
                    .thenApply(e -> foldEither(e, Long::sum));
        }

        return awaitValues(
                depsCommand.fetchDependencyTree(artifacts, null, scopes, true, optional, exclusions)
                        .values().stream()
                        .map((completion) -> completion.thenCompose(tree ->
                                tree.map(a -> install(a, checksum, false)).orElseGet(() ->
                                        completedStage(0L))))
                        .collect(toList())
        ).thenApply(this::groupErrors)
                .thenApply(e -> foldEither(e, Long::sum));
    }

    private static boolean includesMavenRepositoryWriter(ArtifactFileWriter writer) {
        return writer.mode == ArtifactFileWriter.WriteMode.MAVEN_REPOSITORY ||
                (writer instanceof MultiArtifactFileWriter
                        && includesMavenRepositoryWriter(((MultiArtifactFileWriter) writer).secondWriter));
    }

    private Collection<Either<Long, NonEmptyCollection<Throwable>>> groupErrors(
            Collection<Either<Long, Throwable>> completions) {
        return completions.stream()
                .map(comp -> comp.mapRight(NonEmptyCollection::of))
                .collect(toList());
    }

    private CompletionStage<Long> install(DependencyTree tree,
                                          boolean checksum,
                                          boolean mustInstallPom) {
        var treeSet = tree.toSet().stream()
                .flatMap(dep -> artifactsToFetchFrom(dep, checksum, mustInstallPom))
                .collect(toSet());

        log.verbosePrintln(() -> "Will install " + treeSet.size() +
                " artifact" + (treeSet.size() == 1 ? "" : "s") + " at " + writer.getDestination());

        return awaitValues(
                fetchCommand.fetchArtifacts(treeSet, writer, false)
        ).thenApply(results -> checkResultsCountingSuccess(results, checksum));
    }

    private long checkResultsCountingSuccess(
            Map<Artifact, Either<Optional<ResolvedArtifact>, Throwable>> results,
            boolean checksum) {
        if (checksum) {
            return verifyChecksumCountingSuccess(results);
        }
        long successCount = 0;
        for (var entry : results.entrySet()) {
            var resolvedCount = entry.getValue().map(ok -> ok.map(a -> 1).orElse(0), err -> 0);
            successCount += resolvedCount;
            if (resolvedCount == 0) {
                log.println(() -> "Failed to fetch " + entry.getKey().getCoordinates());
            }
        }
        return successCount;
    }

    private long verifyChecksumCountingSuccess(
            Map<Artifact, Either<Optional<ResolvedArtifact>, Throwable>> results) {
        class Sha1 {
            final ResolvedArtifact resolved;
            ResolvedArtifact sha1;

            Sha1(ResolvedArtifact resolved) {
                this.resolved = resolved;
            }
        }

        final var successCount = new AtomicLong(0L);
        var checksumByArtifact = new HashMap<Artifact, Sha1>(results.size() / 2);

        // put the non-checksum files in the Map
        for (var e : results.entrySet()) {
            var resolved = e.getValue().map(ok -> ok.orElse(null), err -> null);
            if (resolved != null && !resolved.artifact.isSha1()) {
                checksumByArtifact.computeIfAbsent(e.getKey(), ignore -> new Sha1(resolved));
            }
        }

        // try to match the checksums with the artifacts in the Map
        for (var e : results.entrySet()) {
            var resolved = e.getValue().map(ok -> ok.orElse(null), err -> null);
            if (resolved != null && resolved.artifact.isSha1()) {
                var sha = checksumByArtifact.get(resolved.artifact.noSha1());
                if (sha == null) {
                    throw new IllegalStateException("Did not find checksum for " + e.getKey().getCoordinates());
                } else {
                    sha.sha1 = resolved;
                }
            }
        }

        // verify the checksums
        for (var entry : checksumByArtifact.entrySet()) {
            var artifact = entry.getKey();
            var shaEntry = entry.getValue();
            if (shaEntry.sha1 != null) {
                var result = ChecksumVerifier.verify(shaEntry.resolved, shaEntry.sha1, log.isVerbose());
                result.use(ok -> {
                    successCount.incrementAndGet();
                    log.verbosePrintln(() -> "Artifact " + artifact.getCoordinates() +
                            "'s checksum successfully verified.");
                }, errors -> {
                    log.println("ERROR: " + errors.stream().map(Describable::getDescription).collect(joining(", ")));
                    if (!writer.delete(shaEntry.resolved.artifact.noSha1())) {
                        log.println("WARNING: Could not delete " + artifact.getCoordinates() +
                                " (invalid checksum was detected - do not use installed files).");
                    }
                    writer.delete(shaEntry.resolved.artifact.sha1());
                });
            }
        }

        return successCount.get();
    }

    private static Stream<Artifact> artifactsToFetchFrom(ResolvedDependency dep,
                                                         boolean checksum,
                                                         boolean mustInstallPom) {
        var mainArtifact = dep.artifact.withExtension(
                dep.pom == null ? "jar" : extensionOfPackaging(dep.pom.getPackaging()));
        Stream<Artifact> artifacts;
        if (checksum && !mainArtifact.isSha1()) {
            artifacts = Stream.of(mainArtifact, mainArtifact.sha1());
        } else {
            artifacts = Stream.of(mainArtifact);
        }
        if (mustInstallPom && !mainArtifact.isPom()) {
            var pom = mainArtifact.pom();
            return Stream.concat(artifacts, Stream.of(pom, pom.sha1()));
        }
        return artifacts;
    }

}
