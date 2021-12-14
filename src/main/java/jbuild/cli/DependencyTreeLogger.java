package jbuild.cli;

import jbuild.artifact.Artifact;
import jbuild.log.JBuildLog;
import jbuild.maven.ArtifactKey;
import jbuild.maven.Dependency;
import jbuild.maven.DependencyTree;
import jbuild.maven.Scope;
import jbuild.util.NonEmptyCollection;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static jbuild.util.CollectionUtils.sorted;

final class DependencyTreeLogger {

    private static final String INDENT = "    ";

    private final JBuildLog log;
    private final boolean transitive;

    DependencyTreeLogger(JBuildLog log, boolean transitive) {
        this.log = log;
        this.transitive = transitive;
    }

    public void log(DependencyTree tree) {
        log.println("Dependencies of " + tree.root.artifact.getCoordinates() +
                (transitive ? " (incl. transitive)" : "") + ":");

        var deps = tree.root.pom.getDependencies();

        if (deps.isEmpty()) {
            log.println("  * no dependencies");
            return;
        }

        var groupedDeps = deps.stream()
                .collect(groupingBy(dep -> dep.scope));

        // iterated sorted by scope declaration order
        for (Scope scope : Scope.values()) {
            var scopeDeps = groupedDeps.get(scope);
            if (scopeDeps != null && !scopeDeps.isEmpty()) {
                log.println("  - scope " + scope);
                if (transitive) {
                    var visitedDeps = new HashSet<Artifact>();
                    logTree(visitedDeps, scopeDeps, tree.dependencies, INDENT, scope);
                } else {
                    logChildren(tree);
                }
            }
        }
    }

    private void logChildren(DependencyTree tree) {
        var children = tree.root.pom.getDependencies();
        for (var child : sorted(children, comparing(dep -> dep.artifact.getCoordinates()))) {
            log.println(INDENT + "* " + child.artifact.getCoordinates());
        }
    }

    private void logTree(Set<Artifact> visitedDeps, Collection<Dependency> scopeDeps, List<DependencyTree> children, String indent, Scope scope) {
        var childByKey = children.stream()
                .collect(toMap(c -> ArtifactKey.of(c.root.artifact),
                        NonEmptyCollection::of, NonEmptyCollection::of));

        for (var dep : sorted(scopeDeps, comparing(dep -> dep.artifact.getCoordinates()))) {
            log.print(() -> indent + "* " + dep.artifact.getCoordinates() + " [" + dep.scope + "]");

            var isNew = visitedDeps.add(dep.artifact);

            if (isNew) {
                var nextBranch = childByKey.get(ArtifactKey.of(dep));
                if (nextBranch == null) {
                    log.println(" (X)");
                } else {
                    log.println("");
                    var nextDeps = nextBranch.first.root.pom.getDependencies(scope);
                    logTree(visitedDeps, nextDeps, nextBranch.first.dependencies, indent + INDENT, scope);
                }
            } else {
                log.println(" (-)");
            }
        }
    }
}
