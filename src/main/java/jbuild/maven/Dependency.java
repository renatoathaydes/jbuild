package jbuild.maven;

import jbuild.artifact.Artifact;

public final class Dependency {

    public final Artifact artifact;
    public final Scope scope;
    public final boolean optional;

    // keep the original String value so we can resolve it if needed
    final String optionalString;

    public Dependency(Artifact artifact, Scope scope, boolean optional) {
        this.artifact = artifact;
        this.scope = scope;
        this.optional = optional;
        this.optionalString = Boolean.toString(optional);
    }

    Dependency(Artifact artifact, Scope scope, String optionalString) {
        this.artifact = artifact;
        this.scope = scope;
        this.optional = "true".equals(optionalString);
        this.optionalString = optionalString;
    }

    public Dependency(Artifact artifact) {
        this(artifact, Scope.COMPILE, false);
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
                ", optional=" + optional +
                '}';
    }
}
