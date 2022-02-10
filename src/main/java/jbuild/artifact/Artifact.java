package jbuild.artifact;

import static jbuild.util.TextUtils.firstNonBlank;
import static jbuild.util.TextUtils.requireNonBlank;

public class Artifact {

    public final String groupId;
    public final String artifactId;
    public final String version;
    public final String extension;

    private final String coordinates;

    public Artifact(String groupId, String artifactId, String version, String extension) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.extension = firstNonBlank(extension, "jar");
        this.coordinates = groupId + ':' + artifactId + ':' + version;
    }

    public Artifact(String groupId, String artifactId, String version) {
        this(groupId, artifactId, version, "jar");
    }

    public Artifact(String groupId, String artifactId) {
        this(groupId, artifactId, "", "");
    }

    public Artifact mergeWith(Artifact other) {
        return new Artifact(firstNonBlank(groupId, other.groupId),
                firstNonBlank(artifactId, other.artifactId),
                firstNonBlank(version, other.version),
                firstNonBlank(extension, other.extension));
    }

    public Artifact pom() {
        return withExtension("pom");
    }

    public Artifact jar() {
        return withExtension("jar");
    }

    public Artifact withExtension(String ext) {
        if (ext.equals(extension)) {
            return this;
        }
        return new Artifact(groupId, artifactId, version, ext);
    }

    public Artifact withVersion(Version ver) {
        var verString = ver.toString();
        if (verString.equals(version)) {
            return this;
        }
        return new Artifact(groupId, artifactId, verString, extension);
    }

    public static Artifact parseCoordinates(String artifact) {
        var coordinates = artifact.split(":");
        String groupId = "", artifactId = "";
        if (coordinates.length > 1) {
            groupId = requireNonBlank(coordinates[0], "groupId");
            artifactId = requireNonBlank(coordinates[1], "artifactId");
        }
        switch (coordinates.length) {
            case 2:
                return new Artifact(groupId, artifactId);
            case 3:
                return new Artifact(groupId, artifactId, coordinates[2]);
            case 4:
                return new Artifact(groupId, artifactId, coordinates[2], coordinates[3]);
            default:
                throw new IllegalArgumentException("Cannot parse coordinates, expected 2 to 4 parts" +
                        " (groupId:artifactId[:version[:extension]]), found " +
                        coordinates.length + " part(s) in '" + artifact + "'");
        }
    }

    /**
     * @return a standard file name for this artifact (artifactId-version.extension)
     */
    public String toFileName() {
        return artifactId + (version.isBlank() ? "" : "-" + version) + "." + extension;
    }

    @Override
    public String toString() {
        return "Artifact{" + getCoordinates() + ':' + extension + '}';
    }

    public String getCoordinates() {
        return coordinates;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Artifact artifact = (Artifact) o;

        if (!groupId.equals(artifact.groupId)) return false;
        if (!artifactId.equals(artifact.artifactId)) return false;
        if (!version.equals(artifact.version)) return false;
        return extension.equals(artifact.extension);
    }

    @Override
    public int hashCode() {
        int result = groupId.hashCode();
        result = 31 * result + artifactId.hashCode();
        result = 31 * result + version.hashCode();
        result = 31 * result + extension.hashCode();
        return result;
    }
}
