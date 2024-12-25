package jbuild.commands;

import jbuild.artifact.Artifact;
import jbuild.artifact.ResolvedArtifact;
import jbuild.artifact.file.ArtifactFileWriter;
import jbuild.log.JBuildLog;
import jbuild.maven.DependencyTree;
import jbuild.maven.ResolvedDependency;
import jbuild.maven.Scope;
import jbuild.util.Either;
import jbuild.util.NonEmptyCollection;
import jbuild.util.SHA1;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.completedStage;
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
            Set<Pattern> exclusions,
            boolean checksum) {
        var depsCommand = new DepsCommandExecutor<>(log, new MavenPomRetriever<>(log, fetchCommand, writer));

        return awaitValues(
                depsCommand.fetchDependencyTree(artifacts, null, scopes, transitive, optional, exclusions)
                        .values().stream()
                        .map((completion) -> completion.thenCompose(tree ->
                                tree.map(a -> install(a, checksum)).orElseGet(() ->
                                        completedStage(0L))))
                        .collect(toList())
        ).thenApply(this::groupErrors)
                .thenApply(e -> foldEither(e, Long::sum));
    }

    private Collection<Either<Long, NonEmptyCollection<Throwable>>> groupErrors(
            Collection<Either<Long, Throwable>> completions) {
        return completions.stream()
                .map(comp -> comp.mapRight(NonEmptyCollection::of))
                .collect(toList());
    }

    private CompletionStage<Long> install(DependencyTree tree, boolean checksum) {
        var treeSet = tree.toSet().stream()
                .flatMap(dep -> artifactsToFetchFrom(dep, checksum))
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
            byte[] actual;
            byte[] expected;

            Sha1(ResolvedArtifact resolved) {
                this.resolved = resolved;
            }
        }

        long successCount = 0;
        var checksumByArtifact = new HashMap<Artifact, Sha1>(results.size() / 2);

        for (var e : results.entrySet()) {
            var resolved = e.getValue().map(ok -> ok.orElse(null), err -> null);
            if (resolved != null) {
                var sha1 = checksumByArtifact.computeIfAbsent(e.getKey().noSha1(), ignore -> new Sha1(resolved));
                if (resolved.artifact.isSha1()) {
                    try {
                        sha1.expected = SHA1.fromSha1StringBytes(resolved.consumeContentsToArray());
                    } catch (IllegalArgumentException ignore) {
                        log.println("WARNING: Checksum of " + resolved.artifact.getCoordinates() + " is invalid");
                    }
                } else {
                    sha1.actual = SHA1.computeSha1(resolved.consumeContentsToArray());
                }
            }
        }

        for (var entry : checksumByArtifact.entrySet()) {
            var artifact = entry.getKey();
            var sha1 = entry.getValue();
            if (sha1.actual != null && sha1.expected != null) {
                if (Arrays.equals(sha1.actual, sha1.expected)) {
                    successCount++;
                    log.verbosePrintln(() -> "Artifact " + artifact.getCoordinates() + "'s checksum successfully verified.");
                } else {
                    log.println("ERROR: Checksum of " + artifact.getCoordinates() + " does not match expected value.");
                    if (!writer.delete(sha1.resolved.artifact.noSha1())) {
                        log.println("WARNING: Could not delete " + artifact.getCoordinates() +
                                " (invalid checksum was detected - do not use installed files).");
                    }
                    writer.delete(sha1.resolved.artifact.sha1());
                }
            } else {
                log.println("WARNING: Artifact " + artifact.getCoordinates() + "'s checksum could not be verified." +
                        " actual=" + Arrays.toString(sha1.actual) + ", expected=" + Arrays.toString(sha1.expected));
            }
        }

        return successCount;
    }

    private static Stream<Artifact> artifactsToFetchFrom(ResolvedDependency dep, boolean checksum) {
        var mainArtifact = dep.artifact.withExtension(extensionOfPackaging(dep.pom.getPackaging()));
        if (checksum && !mainArtifact.isSha1()) {
            return Stream.of(mainArtifact, mainArtifact.sha1());
        }
        return Stream.of(mainArtifact);
    }

}
