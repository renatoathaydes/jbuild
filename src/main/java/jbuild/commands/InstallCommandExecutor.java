package jbuild.commands;

import jbuild.artifact.Artifact;
import jbuild.artifact.ResolvedArtifact;
import jbuild.artifact.file.ArtifactFileWriter;
import jbuild.commands.MavenPomRetriever.DefaultPomCreator;
import jbuild.log.JBuildLog;
import jbuild.maven.DependencyTree;
import jbuild.maven.MavenPom;
import jbuild.maven.Scope;
import jbuild.util.Either;
import jbuild.util.NonEmptyCollection;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedStage;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static jbuild.artifact.file.ArtifactFileWriter.WriteMode.FLAT_DIR;
import static jbuild.maven.MavenUtils.extensionOfPackaging;
import static jbuild.maven.Scope.expandScopes;
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
            boolean optional) {

        var pomRetriever = new MavenPomRetriever<>(log, fetchCommand,
                // only write POMs if not using a flat dir output
                writer.mode == FLAT_DIR ? new DefaultPomCreator() : new InstallPomCreator(writer));

        var depsCommand = new DepsCommandExecutor<>(log, pomRetriever);
        var expandedScopes = expandScopes(scopes);

        return awaitValues(
                depsCommand.fetchDependencyTree(artifacts, expandedScopes, transitive, optional)
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

    private static final class InstallPomCreator extends DefaultPomCreator {

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
