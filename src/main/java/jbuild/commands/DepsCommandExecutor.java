package jbuild.commands;

import jbuild.artifact.Artifact;
import jbuild.errors.ArtifactRetrievalError;
import jbuild.log.JBuildLog;
import jbuild.maven.ArtifactKey;
import jbuild.maven.Dependency;
import jbuild.maven.DependencyTree;
import jbuild.maven.MavenPom;
import jbuild.util.Either;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static jbuild.util.AsyncUtils.awaitValues;
import static jbuild.util.CollectionUtils.mapEntries;

public class DepsCommandExecutor<Err extends ArtifactRetrievalError> {

    private final JBuildLog log;
    private final MavenPomRetriever<Err> mavenPomRetriever;

    public DepsCommandExecutor(JBuildLog log,
                               MavenPomRetriever<Err> mavenPomRetriever) {
        this.log = log;
        this.mavenPomRetriever = mavenPomRetriever;
    }

    public static DepsCommandExecutor<? extends ArtifactRetrievalError> createDefault(JBuildLog log) {
        var mavenPomRetriever = new MavenPomRetriever<>(log, FetchCommandExecutor.createDefault(log));
        return new DepsCommandExecutor<>(log, mavenPomRetriever);
    }

    public Map<Artifact, CompletionStage<Optional<DependencyTree>>> fetchDependencyTree(
            Set<? extends Artifact> artifacts, boolean transitive) {
        return mapEntries(mavenPomRetriever.fetchPoms(artifacts),
                (artifact, completionStage) -> completionStage.thenComposeAsync(pom -> {
                    if (pom.isEmpty()) return completedFuture(Optional.empty());
                    return fetchChildren(new Dependency(artifact, null), pom.get(), transitive);
                }));
    }

    private CompletionStage<Optional<DependencyTree>> fetchChildren(
            Dependency dependency,
            MavenPom pom,
            boolean transitive) {
        log.verbosePrintln(() -> "Fetching dependencies of " + dependency.artifact.getCoordinates() +
                (dependency.scope == null ? " under all scopes" : " under scope " + dependency.scope));

        return awaitValues(fetchChildren(
                pom.getDependencies(dependency.scope), transitive)).thenApply(completions ->
                treeIfSuccess(dependency, completions.values()));
    }

    private Map<Artifact, CompletionStage<Optional<DependencyTree>>> fetchChildren(
            Set<? extends Dependency> dependencies,
            boolean transitive) {
        var depByArtifact = dependencies.stream()
                .collect(toMap(d -> ArtifactKey.of(d.artifact), d -> d));

        var artifacts = dependencies.stream().map(d -> d.artifact).collect(toSet());

        return mapEntries(mavenPomRetriever.fetchPoms(artifacts), (artifact, completionStage) ->
                completionStage.thenComposeAsync(pom -> {
                    if (pom.isEmpty()) return completedFuture(Optional.empty());
                    var dep = depByArtifact.get(ArtifactKey.of(artifact));
                    return transitive
                            ? fetchChildren(dep, pom.get(), true)
                            : completedFuture(Optional.of(DependencyTree.of(pom.get())));
                }));
    }

    private Optional<DependencyTree> treeIfSuccess(Dependency dependency,
                                                   Collection<Either<Optional<DependencyTree>, Throwable>> children) {
        var childrenNodes = new ArrayList<DependencyTree>();
        var allOk = true;
        for (var child : children) {
            allOk &= child.map(ok -> {
                if (ok.isEmpty()) {
                    log.println(() -> "Unable to populate dependencies of " + dependency.artifact.getCoordinates() +
                            ", not all artifacts could be successfully retrieved");
                    return false;
                } else {
                    childrenNodes.add(ok.get());
                    return true;
                }
            }, err -> {
                log.println(() -> "Unable to populate dependencies of " + dependency.artifact.getCoordinates() +
                        " due to: " + err);
                if (err != null) log.print(err);
                return false;
            });
        }
        if (allOk) {
            return Optional.of(new DependencyTree(dependency, childrenNodes));
        }
        return Optional.empty();
    }

}
