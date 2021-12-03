package jbuild.maven;

import jbuild.artifact.Artifact;

public final class Dependency {

    private final Artifact artifact;
    private final Scope scope;

    public Dependency(Artifact artifact, Scope scope) {
        this.artifact = artifact;
        this.scope = scope;
    }

    public Dependency(Artifact artifact) {
        this(artifact, Scope.COMPILE);
    }

    public Artifact getArtifact() {
        return artifact;
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Dependency that = (Dependency) o;

        if (!artifact.equals(that.artifact)) return false;
        return scope == that.scope;
    }

    @Override
    public int hashCode() {
        int result = artifact.hashCode();
        result = 31 * result + scope.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Dependency{" +
                "artifact=" + artifact +
                ", scope=" + scope +
                '}';
    }
}
