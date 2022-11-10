package jbuild.commands;

import jbuild.artifact.Artifact;
import jbuild.artifact.file.ArtifactFileWriter;
import jbuild.log.JBuildLog;
import jbuild.maven.DependencyTree;
import jbuild.maven.Scope;
import jbuild.util.Either;
import jbuild.util.NonEmptyCollection;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

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
            boolean transitive,
            boolean optional,
            Set<Pattern> exclusions) {
        var pomRetriever = new MavenPomRetriever<>(log, fetchCommand, writer);

        var depsCommand = new DepsCommandExecutor<>(log, pomRetriever);

        return awaitValues(
                depsCommand.fetchDependencyTree(artifacts, scopes, transitive, optional, exclusions)
                        .values().stream()
                        .map((completion) -> completion.thenCompose(tree ->
                                tree.map(this::install).orElseGet(() ->
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

    private CompletionStage<Long> install(DependencyTree tree) {
        var treeSet = tree.toSet().stream()
                .map(dep -> dep.artifact.withExtension(extensionOfPackaging(dep.pom.getPackaging())))
                .collect(toSet());

        log.println(() -> "Will install " + treeSet.size() +
                " artifact" + (treeSet.size() == 1 ? "" : "s") + " at " + writer.getDestination());

        return awaitValues(
                fetchCommand.fetchArtifacts(treeSet, writer)
                        .entrySet().stream().map(entry -> entry.getValue()
                                .thenApply(resolved -> {
                                    if (resolved.isEmpty()) {
                                        log.println(() -> "Failed to fetch " + entry.getKey());
                                        return false;
                                    }
                                    return true; // success
                                })).collect(toList())
        ).thenApply(results -> results.stream()
                .filter(res -> res.map(isSuccess -> isSuccess, err -> false))
                .count());
    }

}
