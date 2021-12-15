package jbuild.maven;

import jbuild.artifact.Artifact;

import java.util.Set;

public final class Dependency {

    public final Artifact artifact;
    public final Scope scope;
    public final boolean optional;
    public final Set<ArtifactKey> exclusions;

    // keep the original String value so we can resolve it if needed
    final String optionalString;

    public Dependency(Artifact artifact, Scope scope, boolean optional, Set<ArtifactKey> exclusions) {
        this.artifact = artifact;
        this.scope = scope;
        this.optional = optional;
        this.optionalString = Boolean.toString(optional);
        this.exclusions = exclusions;
    }

    Dependency(Artifact artifact, Scope scope, String optionalString, Set<ArtifactKey> exclusions) {
        this.artifact = artifact;
        this.scope = scope;
        this.optional = "true".equals(optionalString);
        this.optionalString = optionalString;
        this.exclusions = exclusions;
    }

    public Dependency(Artifact artifact) {
        this(artifact, Scope.COMPILE, false, Set.of());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Dependency that = (Dependency) o;

        if (optional != that.optional) return false;
        if (!artifact.equals(that.artifact)) return false;
        if (scope != that.scope) return false;
        if (!exclusions.equals(that.exclusions)) return false;
        return optionalString.equals(that.optionalString);
    }

    @Override
    public int hashCode() {
        int result = artifact.hashCode();
        result = 31 * result + scope.hashCode();
        result = 31 * result + (optional ? 1 : 0);
        result = 31 * result + exclusions.hashCode();
        result = 31 * result + optionalString.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Dependency{" +
                "artifact=" + artifact +
                ", scope=" + scope +
                ", optional=" + optional +
                ", exclusions=" + exclusions +
                '}';
    }
}
