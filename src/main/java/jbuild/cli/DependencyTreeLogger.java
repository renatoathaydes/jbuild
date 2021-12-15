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
    private final DepsOptions options;

    DependencyTreeLogger(JBuildLog log, DepsOptions options) {
        this.log = log;
        this.options = options;
    }

    public void log(DependencyTree tree) {
        log.print("Dependencies of " + tree.root.artifact.getCoordinates());

        if (options.transitive && options.optional) {
            log.println(" (incl. transitive, optionals):");
        } else if (options.transitive) {
            log.println(" (incl. transitive):");
        } else if (options.optional) {
            log.println(" (incl. optionals):");
        } else {
            log.println(":");
        }

        var deps = tree.root.pom.getDependencies(options.scopes, options.optional);

        if (deps.isEmpty()) {
            log.println("  * no dependencies");
            return;
        }

        var groupedDeps = deps.stream()
                .collect(groupingBy(dep -> dep.scope));

        for (Scope scope : options.scopes) {
            var scopeDeps = groupedDeps.get(scope);
            if (scopeDeps != null && !scopeDeps.isEmpty()) {
                int dependencyCount;
                log.println("  - scope " + scope);
                if (options.transitive) {
                    var visitedDeps = new HashSet<Artifact>();
                    logTree(visitedDeps, scopeDeps, tree.dependencies, INDENT, scope);
                    dependencyCount = visitedDeps.size();
                } else {
                    var children = tree.root.pom
                            .getDependencies(scope.transitiveScopes(), options.optional);
                    logChildren(children);
                    dependencyCount = children.size();
                }
                log.println(() -> "  " + dependencyCount + " " + scope +
                        " dependenc" + (dependencyCount == 1 ? "y" : "ies") + " listed");
            }
        }
    }

    private void logChildren(Set<Dependency> children) {
        for (var child : sorted(children, comparing(dep -> dep.artifact.getCoordinates()))) {
            log.println(INDENT + "* " + child.artifact.getCoordinates());
        }
    }

    private void logTree(Set<Artifact> visitedDeps,
                         Collection<Dependency> scopeDeps,
                         List<DependencyTree> children,
                         String indent,
                         Scope scope) {
        var childByKey = children.stream()
                .collect(toMap(c -> ArtifactKey.of(c.root.artifact),
                        NonEmptyCollection::of, NonEmptyCollection::of));

        for (var dep : sorted(scopeDeps, comparing(dep -> dep.artifact.getCoordinates()))) {
            log.print(() -> indent + "* " + dep.artifact.getCoordinates() +
                    " [" + dep.scope + "]" +
                    (dep.optional ? "[optional]" : ""));

            var isNew = visitedDeps.add(dep.artifact);

            if (isNew) {
                var nextBranch = childByKey.get(ArtifactKey.of(dep));
                if (nextBranch == null) {
                    log.println(" (X)");
                } else {
                    log.println("");
                    for (var next : nextBranch) {
                        var nextDeps = next.root.pom
                                .getDependencies(scope.transitiveScopes(), options.optional);
                        logTree(visitedDeps, nextDeps, next.dependencies, indent + INDENT, scope);
                    }
                }
            } else {
                log.println(" (-)");
            }
        }
    }
}
