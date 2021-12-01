package jbuild.artifact;

public class Artifact {
    public final String groupId;
    public final String artifactId;
    public final String version;
    public final String extension;

    public Artifact(String groupId, String artifactId, String version, String extension) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.extension = extension;
    }

    public Artifact(String groupId, String artifactId, String version) {
        this(groupId, artifactId, version, "jar");
    }

    public Artifact(String groupId, String artifactId) {
        this(groupId, artifactId, "", "jar");
    }

    public static Artifact parseCoordinates(String artifact) {
        var coordinates = artifact.split(":");
        switch (coordinates.length) {
            case 2:
                return new Artifact(coordinates[0], coordinates[1]);
            case 3:
                return new Artifact(coordinates[0], coordinates[1], coordinates[2]);
            case 4:
                return new Artifact(coordinates[0], coordinates[1], coordinates[2], coordinates[3]);
            default:
                throw new IllegalArgumentException("Cannot parse coordinates, expected 2 to 4 parts, found " +
                        coordinates.length + " in '" + artifact + "'");
        }
    }

    /**
     * @return a standard file name for this artifact (artifactId-version.extension)
     */
    public String toFileName() {
        return artifactId + "-" + version + "." + extension;
    }

    @Override
    public String toString() {
        return "Artifact{" + groupId + ':' + artifactId + ':' + version + ':' + extension + '}';
    }
}
