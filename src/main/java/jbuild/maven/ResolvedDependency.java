package jbuild.maven;

import jbuild.artifact.Artifact;

public final class ResolvedDependency {

    public final Artifact artifact;
    public final MavenPom pom;

    public ResolvedDependency(Artifact artifact, MavenPom pom) {
        this.artifact = artifact;
        this.pom = pom;
    }

    @Override
    public String toString() {
        return "ResolvedDependency{" +
                "artifact=" + artifact.getCoordinates() +
                ", pom=" + pom +
                '}';
    }
}
