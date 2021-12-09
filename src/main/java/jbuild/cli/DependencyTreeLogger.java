package jbuild.cli;

import jbuild.log.JBuildLog;
import jbuild.maven.DependencyTree;
import jbuild.maven.Scope;

import java.util.List;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static jbuild.util.CollectionUtils.sorted;

final class DependencyTreeLogger {

    private final JBuildLog log;

    DependencyTreeLogger(JBuildLog log) {
        this.log = log;
    }

    public void log(DependencyTree tree) {
        log.println("Dependencies of " + tree.dependency.artifact.getCoordinates() + ":");

        if (tree.dependencies.isEmpty()) {
            log.println("  * no dependencies");
            return;
        }

        var groupedDeps = tree.dependencies.stream()
                .collect(groupingBy(node -> node.dependency.scope));

        // iterated sorted by scope declaration order
        for (Scope scope : Scope.values()) {
            var deps = groupedDeps.get(scope);
            if (deps != null && !deps.isEmpty()) {
                log.println("  - scope " + scope);
                logBranch(deps, "    ", scope);
            }
        }
    }

    private void logBranch(List<DependencyTree> deps, String indent, Scope scope) {
        for (var node : sorted(deps, comparing(node -> node.dependency.artifact.getCoordinates()))) {
            if (node.dependency.scope == scope) {
                log.println(indent + "* " + node.dependency.artifact.getCoordinates());
                logBranch(node.dependencies, indent + "  ", scope);
            }
        }
    }
}
