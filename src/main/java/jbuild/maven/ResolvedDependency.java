package jbuild.maven;

public final class ResolvedDependency {

    public final Dependency dependency;
    public final MavenPom pom;

    public ResolvedDependency(Dependency dependency, MavenPom pom) {
        this.dependency = dependency;
        this.pom = pom;
    }

    @Override
    public String toString() {
        return "ResolvedDependency{" +
                "artifact=" + dependency.artifact +
                ", scope=" + dependency.scope +
                ", pom=" + pom +
                '}';
    }
}
