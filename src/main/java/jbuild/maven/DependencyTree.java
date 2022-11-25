package jbuild.maven;

import jbuild.artifact.Artifact;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A tree of dependencies.
 * <p>
 * Each node in a tree is a {@link DependencyTree} itself. It consists of a root and its possibly empty children.
 */
public final class DependencyTree {

    public final ResolvedDependency root;
    public final List<DependencyTree> dependencies;

    private DependencyTree(ResolvedDependency root,
                           List<DependencyTree> dependencies) {
        this.root = root;
        this.dependencies = dependencies;
    }

    public String displayVersion() {
        var resolvedVersion = root.pom.getArtifact().version;
        var requestedVersion = root.artifact.version;
        if (!requestedVersion.equals(resolvedVersion)) {
            return requestedVersion + " -> " + resolvedVersion;
        }
        return requestedVersion;
    }

    /**
     * @return a Set containing the flattened elements of this dependency tree. Notice that the tree may contain
     * the same artifact with multiple versions as no version clash resolution is performed.
     */
    public Set<ResolvedDependency> toSet() {
        var result = new HashSet<ResolvedDependency>();
        visitArtifacts(this, result);
        return result;
    }

    private static void visitArtifacts(DependencyTree node, Set<ResolvedDependency> result) {
        var current = List.of(node);
        while (!current.isEmpty()) {
            for (var dependency : current) {
                result.add(dependency.root);
            }
            current = current.stream()
                .flatMap(c -> c.dependencies.stream())
                .collect(Collectors.toList());
        }
    }

    /**
     * Create a resolved dependency tree.
     * <p>
     * A resolved dependency tree may be missing children in case any failed to resolve successfully.
     * To know which children should be in the list of dependencies, use
     * {@link MavenPom#getDependencies()}.
     *
     * @param artifact     the root of the tree
     * @param pom          of the given dependency
     * @param dependencies the resolved child dependencies
     * @return resolved tree of dependencies
     */
    public static DependencyTree resolved(Artifact artifact, MavenPom pom, List<DependencyTree> dependencies) {
        return new DependencyTree(new ResolvedDependency(artifact.pom(), pom), dependencies);
    }

    /**
     * Create a non-resolved dependency tree (i.e. without resolved children).
     *
     * @param artifact the root of the tree
     * @param pom      of the given dependency
     * @return single-node tree without resolved children
     */
    public static DependencyTree childless(Artifact artifact, MavenPom pom) {
        return new DependencyTree(new ResolvedDependency(artifact.pom(), pom), List.of());
    }
}
