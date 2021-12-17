package jbuild.commands;

import jbuild.artifact.Artifact;
import jbuild.artifact.ResolvedArtifact;
import jbuild.artifact.file.ArtifactFileWriter;
import jbuild.log.JBuildLog;
import jbuild.maven.DependencyTree;
import jbuild.maven.MavenPom;
import jbuild.maven.Scope;
import jbuild.util.CollectionUtils;
import jbuild.util.Either;
import jbuild.util.NonEmptyCollection;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedStage;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static jbuild.maven.MavenUtils.extensionOfPackaging;
import static jbuild.maven.Scope.expandScopes;
import static jbuild.util.AsyncUtils.awaitValues;

public final class InstallCommandExecutor {

    private final JBuildLog log;
    private final FetchCommandExecutor<?> fetchCommand;
    private final DepsCommandExecutor<?> depsCommand;
    private final ArtifactFileWriter writer;

    public InstallCommandExecutor(JBuildLog log,
                                  FetchCommandExecutor<?> fetchCommand,
                                  ArtifactFileWriter writer) {
        this.log = log;
        this.fetchCommand = fetchCommand;
        this.writer = writer;
        var pomRetriever = new MavenPomRetriever<>(log, fetchCommand, new InstallPomCreator(writer));
        this.depsCommand = new DepsCommandExecutor<>(log, pomRetriever);
    }

    public static InstallCommandExecutor create(JBuildLog log,
                                                ArtifactFileWriter writer) {

        return new InstallCommandExecutor(log, FetchCommandExecutor.createDefault(log), writer);
    }

    public CompletionStage<Either<Long, NonEmptyCollection<Throwable>>> installDependencyTree(
            Set<? extends Artifact> artifacts,
            EnumSet<Scope> scopes,
            boolean transitive,
            boolean optional) {
        var expandedScopes = expandScopes(scopes);

        return awaitValues(
                depsCommand.fetchDependencyTree(artifacts, expandedScopes, transitive, optional)
                        .values().stream()
                        .map((completion) -> completion.thenCompose(tree ->
                                tree.map(this::install).orElseGet(() ->
                                        completedStage(0L))))
                        .collect(toList())
        ).thenApply(this::groupErrors)
                .thenApply(CollectionUtils::foldEither);
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
                " artifact" + (treeSet.size() == 1 ? "" : "s") + " at " + writer.directory);

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

    private static final class InstallPomCreator extends MavenPomRetriever.DefaultPomCreator {

        private final ArtifactFileWriter writer;

        public InstallPomCreator(ArtifactFileWriter writer) {
            this.writer = writer;
        }

        @Override
        public CompletionStage<MavenPom> createPom(ResolvedArtifact resolvedArtifact) {
            return writer.write(resolvedArtifact, false).thenCompose(ignore ->
                    super.createPom(resolvedArtifact));
        }
    }

}
