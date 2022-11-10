package jbuild.commands;

import jbuild.artifact.Artifact;
import jbuild.commands.MavenPomRetriever.DefaultPomCreator;
import jbuild.errors.ArtifactRetrievalError;
import jbuild.log.JBuildLog;
import jbuild.maven.Dependency;
import jbuild.maven.DependencyTree;
import jbuild.maven.MavenPom;
import jbuild.maven.Scope;
import jbuild.util.Either;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static jbuild.maven.MavenUtils.applyExclusionPatterns;
import static jbuild.maven.MavenUtils.asPatterns;
import static jbuild.maven.Scope.expandScopes;
import static jbuild.util.AsyncUtils.awaitValues;
import static jbuild.util.CollectionUtils.append;
import static jbuild.util.CollectionUtils.mapEntries;
import static jbuild.util.CollectionUtils.union;

public final class DepsCommandExecutor<Err extends ArtifactRetrievalError> {

    private static final int MAX_TREE_DEPTH = 100;

    private final JBuildLog log;
    private final MavenPomRetriever<Err> mavenPomRetriever;

    public DepsCommandExecutor(JBuildLog log,
                               MavenPomRetriever<Err> mavenPomRetriever) {
        this.log = log;
        this.mavenPomRetriever = mavenPomRetriever;
    }

    public static DepsCommandExecutor<ArtifactRetrievalError> createDefault(JBuildLog log) {
        var mavenPomRetriever = new MavenPomRetriever<>(log,
                FetchCommandExecutor.createDefault(log),
                DefaultPomCreator.INSTANCE);
        return new DepsCommandExecutor<>(log, mavenPomRetriever);
    }

    public static <E extends ArtifactRetrievalError> DepsCommandExecutor<E> create(
            JBuildLog log,
            FetchCommandExecutor<E> fetchCommandExecutor) {
        var mavenPomRetriever = new MavenPomRetriever<>(log,
                fetchCommandExecutor,
                DefaultPomCreator.INSTANCE);
        return new DepsCommandExecutor<>(log, mavenPomRetriever);
    }

    public Map<Artifact, CompletionStage<Optional<DependencyTree>>> fetchDependencyTree(
            Set<? extends Artifact> artifacts,
            EnumSet<Scope> scopes,
            boolean transitive,
            boolean optional) {
        return fetchDependencyTree(artifacts, scopes, transitive, optional, Set.of());
    }

    public Map<Artifact, CompletionStage<Optional<DependencyTree>>> fetchDependencyTree(
            Set<? extends Artifact> artifacts,
            EnumSet<Scope> scopes,
            boolean transitive,
            boolean optional,
            Set<Pattern> exclusions) {
        var expandedScopes = expandScopes(scopes);

        log.verbosePrintln(() -> "Fetching dependencies of " + artifacts + " with scopes " + expandedScopes +
                (exclusions.isEmpty() ? "" : " with exclusions " + exclusions));

        return mapEntries(mavenPomRetriever.fetchPoms(artifacts),
                (artifact, completionStage) -> completionStage.thenComposeAsync(pom -> {
                    if (pom.isEmpty()) return completedFuture(Optional.empty());
                    return fetchChildren(Set.of(), artifact, pom.get(), expandedScopes, transitive, optional, exclusions)
                            .thenApply(Optional::of);
                }));
    }

    private CompletionStage<DependencyTree> fetchChildren(
            Set<Dependency> chain,
            Artifact artifact,
            MavenPom pom,
            EnumSet<Scope> scopes,
            boolean transitive,
            boolean includeOptionals,
            Set<Pattern> exclusions) {
        var dependencies = applyExclusionPatterns(pom.getDependencies(scopes, includeOptionals), exclusions);
        var maxTreeDepthExceeded = transitive && chain.size() + 1 > MAX_TREE_DEPTH;

        if (maxTreeDepthExceeded || dependencies.isEmpty()) {
            if (maxTreeDepthExceeded) {
                log.println(() -> "WARNING: Maximum dependency tree depth would be exceeded, " +
                        "will not fetch dependencies of " + artifact.getCoordinates());
            } else {
                log.verbosePrintln(() -> "Artifact " + artifact.getCoordinates() +
                        " does not have any dependencies");
            }
            return completedFuture(DependencyTree.childless(artifact.pom(), pom));
        }

        log.verbosePrintln(() -> "Fetching " + dependencies);

        var children = dependencies.stream()
                .map(dependency -> {
                    var newChain = append(chain, dependency);
                    if (newChain.size() == chain.size()) {
                        log.println(() -> "WARNING: Detected circular dependency chain - " +
                                collectDependencyChain(chain, dependency));
                    } else if (transitive) {
                        log.verbosePrintln(() -> "Fetching dependency of " +
                                artifact.getCoordinates() + " - " + dependency);

                        return fetch(newChain, dependency, includeOptionals, exclusions);
                    }
                    return completedFuture(Optional.of(DependencyTree.childless(dependency.artifact, pom)));
                })
                .collect(toList());

        return awaitValues(children).thenApply(c -> createTree(artifact.pom(), pom, c));
    }

    private CompletionStage<Optional<DependencyTree>> fetch(
            Set<Dependency> chain,
            Dependency dependency,
            boolean includeOptionals,
            Set<Pattern> exclusions) {
        return mavenPomRetriever.fetchPom(dependency.artifact.pom()).thenComposeAsync(child -> {
            if (child.isEmpty()) return completedFuture(Optional.empty());
            var pom = child.get();
            return fetchChildren(chain, dependency.artifact, pom,
                    dependency.scope.transitiveScopes(), true, includeOptionals,
                    union(asPatterns(dependency.exclusions), exclusions)
            ).thenApply(Optional::of);
        });
    }

    private String collectDependencyChain(Set<Dependency> chain, Dependency dependency) {
        return Stream.concat(chain.stream(), Stream.of(dependency))
                .map(d -> d.artifact.getCoordinates())
                .collect(joining(" -> "));
    }

    private DependencyTree createTree(Artifact artifact,
                                      MavenPom mavenPom,
                                      Collection<Either<Optional<DependencyTree>, Throwable>> children) {
        var childrenNodes = new ArrayList<DependencyTree>(children.size());
        var coordinates = artifact.getCoordinates();
        var allOk = true;

        for (var child : children) {
            allOk &= child.map(ok -> {
                if (ok.isEmpty()) {
                    return false;
                }
                childrenNodes.add(ok.get());
                return true;
            }, err -> {
                log.println(() -> "Unable to populate dependencies of " + coordinates +
                        " due to " + err);
                if (err != null) log.print(err);
                return false;
            });
        }

        if (allOk) {
            log.verbosePrintln(() -> "All " + children.size() + " dependencies of " + coordinates +
                    " resolved successfully");
        } else {
            log.verbosePrintln(() -> "Not all dependencies of " + coordinates +
                    " could be resolved successfully");
        }

        // the children may be missing nodes, but we always return the tree anyway as it's easy to
        // match the POM dependencies against the resolved children to see what's missing.
        return DependencyTree.resolved(artifact, mavenPom, childrenNodes);
    }

}
