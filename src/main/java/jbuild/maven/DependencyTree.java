package jbuild.maven;

import java.util.List;

public final class DependencyTree {

    public final ResolvedDependency root;
    public final List<DependencyTree> dependencies;

    private DependencyTree(ResolvedDependency root,
                           List<DependencyTree> dependencies) {
        this.root = root;
        this.dependencies = dependencies;
    }

    /**
     * Create a resolved dependency tree.
     * <p>
     * A resolved dependency tree may be missing children in case any failed to resolve successfully.
     * To know which children should be in the list of dependencies, use
     * {@link Dependency#}
     *
     * @param dependency   the resolved dependency which is the root of the returned tree
     * @param pom          of the given dependency
     * @param dependencies the resolved child dependencies
     * @return resolved tree of dependencies
     */
    public static DependencyTree resolved(Dependency dependency, MavenPom pom, List<DependencyTree> dependencies) {
        return new DependencyTree(new ResolvedDependency(dependency, pom), dependencies);
    }

    /**
     * Create a non-resolved dependency tree (i.e. without resolved children).
     *
     * @param dependency the resolved dependency which is the root of the returned tree
     * @param pom        of the given dependency
     * @return single-node tree without resolved children
     */
    public static DependencyTree of(Dependency dependency, MavenPom pom) {
        return new DependencyTree(new ResolvedDependency(dependency, pom), List.of());
    }

}
