package jbuild.maven;

import jbuild.artifact.ResolvedArtifact;

public final class ResolvedDependency {

    public final ResolvedArtifact resolvedPom;
    private final Scope scope;

    public ResolvedDependency(ResolvedArtifact resolvedPom, Scope scope) {
        assert resolvedPom.artifact.extension.equals("pom");
        this.resolvedPom = resolvedPom;
        this.scope = scope;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ResolvedDependency that = (ResolvedDependency) o;

        if (!resolvedPom.equals(that.resolvedPom)) return false;
        return scope == that.scope;
    }

    @Override
    public int hashCode() {
        int result = resolvedPom.hashCode();
        result = 31 * result + scope.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ResolvedDependency{" +
                "resolvedPom=" + resolvedPom +
                ", scope=" + scope +
                '}';
    }
}
