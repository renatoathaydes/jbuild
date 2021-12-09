package jbuild.maven;

import jbuild.artifact.Artifact;

public final class ArtifactKey {

    private final String groupId;
    private final String artifactId;

    public static ArtifactKey of(Dependency dependency) {
        return of(dependency.artifact);
    }

    public static ArtifactKey of(Artifact artifact) {
        return new ArtifactKey(artifact.groupId, artifact.artifactId);
    }

    public ArtifactKey(String groupId, String artifactId) {
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ArtifactKey that = (ArtifactKey) o;

        return groupId.equals(that.groupId) &&
                artifactId.equals(that.artifactId);
    }

    @Override
    public int hashCode() {
        int result = groupId.hashCode();
        result = 31 * result + artifactId.hashCode();
        return result;
    }
}
