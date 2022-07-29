package jbuild.maven;

import jbuild.artifact.Artifact;

import java.util.HashMap;
import java.util.Map;

/**
 * A key object that can be used to identify a particular {@link Artifact}
 * unambiguously.
 * <p>
 * Notice that the version of an artifact is not part of its key, so different versions of the same artifact
 * will have the same key.
 */
public final class ArtifactKey {

    public final String groupId;
    public final String artifactId;
    public final DependencyType type;

    private static final Map<String, Map<String, Map<DependencyType, ArtifactKey>>> keyCache = new HashMap<>();

    public static ArtifactKey of(Dependency dependency) {
        return of(dependency.artifact.groupId, dependency.artifact.artifactId, dependency.type);
    }

    public static ArtifactKey of(Artifact artifact) {
        return new ArtifactKey(artifact.groupId, artifact.artifactId,
                DependencyType.fromClassifier(artifact.classifier));
    }

    public static ArtifactKey of(String groupId, String artifactId) {
        return of(groupId, artifactId, DependencyType.JAR);
    }

    public static ArtifactKey of(String groupId, String artifactId, DependencyType type) {
        synchronized (keyCache) {
            return keyCache.computeIfAbsent(groupId, g -> new HashMap<>(4))
                    .computeIfAbsent(artifactId, a -> new HashMap<>(2))
                    .computeIfAbsent(type, t -> new ArtifactKey(groupId, artifactId, t));
        }
    }

    private ArtifactKey(String groupId, String artifactId, DependencyType type) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.type = type;
    }

    public String getCoordinates() {
        return groupId + ':' + artifactId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ArtifactKey that = (ArtifactKey) o;

        return type == that.type && groupId.equals(that.groupId) &&
                artifactId.equals(that.artifactId);
    }

    @Override
    public int hashCode() {
        int result = groupId.hashCode();
        result = 31 * result + artifactId.hashCode();
        result = 31 * result + type.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ArtifactKey{" +
                "groupId='" + groupId + '\'' +
                ", artifactId='" + artifactId + '\'' +
                ", type='" + type.name() + '\'' +
                '}';
    }
}
