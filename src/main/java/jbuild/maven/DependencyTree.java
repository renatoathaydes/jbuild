package jbuild.maven;

import java.util.List;

public final class DependencyTree {

    public final Dependency dependency;
    public final List<DependencyTree> dependencies;

    public DependencyTree(Dependency dependency, List<DependencyTree> dependencies) {
        this.dependency = dependency;
        this.dependencies = dependencies;
    }

    /**
     * @param pom to convert to {@link Dependency}
     * @return single-node tree representing a {@link MavenPom}
     */
    public static DependencyTree of(MavenPom pom) {
        return new DependencyTree(new Dependency(pom.getCoordinates()), List.of());
    }

}
