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

public final class DepsCommandExecutor<Err extends ArtifactRetrievalError> {

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
                    return fetchChildren(new Dependency(artifact, null), pom.get(), transitive)
                            .thenApply(Optional::of);
                }));
    }

    private CompletionStage<DependencyTree> fetchChildren(
            Dependency dependency,
            MavenPom pom,
            boolean transitive) {
        log.verbosePrintln(() -> "Fetching dependencies of " + dependency.artifact.getCoordinates() +
                (dependency.scope == null ? " under all scopes" : " under scope " + dependency.scope));

        return awaitValues(fetchChildren(pom.getDependencies(dependency.scope), transitive))
                .thenApply(completions ->
                        createTree(dependency, pom, completions.values()));
    }

    private Map<Artifact, CompletionStage<Optional<DependencyTree>>> fetchChildren(
            Set<? extends Dependency> dependencies,
            boolean transitive) {
        var depByArtifact = dependencies.stream()
                .collect(toMap(d -> ArtifactKey.of(d.artifact), d -> d));

        var artifacts = dependencies.stream().map(d -> d.artifact.pom()).collect(toSet());

        if (artifacts.isEmpty()) {
            return Map.of();
        }

        return mapEntries(mavenPomRetriever.fetchPoms(artifacts), (artifact, completionStage) ->
                completionStage.thenComposeAsync(pom -> {
                    if (pom.isEmpty()) return completedFuture(Optional.empty());
                    var dep = depByArtifact.get(ArtifactKey.of(artifact));
                    return transitive
                            ? fetchChildren(dep, pom.get(), true).thenApply(Optional::of)
                            : completedFuture(Optional.of(DependencyTree.of(dep, pom.get())));
                }));
    }

    private DependencyTree createTree(Dependency dependency,
                                      MavenPom mavenPom,
                                      Collection<Either<Optional<DependencyTree>, Throwable>> children) {
        var childrenNodes = new ArrayList<DependencyTree>(children.size());
        var coordinates = dependency.artifact.getCoordinates();
        var allOk = true;

        for (var child : children) {
            allOk &= child.map(ok -> {
                if (ok.isEmpty()) {
                    log.println(() -> "Unable to populate dependencies of " + coordinates +
                            ", not all artifacts could be successfully retrieved");
                    return false;
                } else {
                    childrenNodes.add(ok.get());
                    return true;
                }
            }, err -> {
                log.println(() -> "Unable to populate dependencies of " + coordinates +
                        " due to: " + err);
                if (err != null) log.print(err);
                return false;
            });
        }

        if (allOk) {
            log.verbosePrintln(() -> "All dependencies of " + coordinates +
                    " resolved successfully");
        } else {
            log.verbosePrintln(() -> "Not all dependencies of " + coordinates +
                    " could be resolved successfully");
        }

        // the children may be missing nodes, but we always return the tree anyway as it's easy to
        // match the POM dependencies against the resolved children to see what's missing.
        return DependencyTree.resolved(dependency, mavenPom, childrenNodes);
    }

}
