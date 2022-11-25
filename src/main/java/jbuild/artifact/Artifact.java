package jbuild.artifact;

import jbuild.errors.JBuildException;
import jbuild.maven.DependencyType;
import jbuild.util.WritableXml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;
import static jbuild.util.TextUtils.firstNonBlank;
import static jbuild.util.TextUtils.requireNonBlank;
import static jbuild.util.TextUtils.trimStart;

public class Artifact implements WritableXml {

    public final String groupId;
    public final String artifactId;
    public final String version;
    public final String extension;
    public final String classifier;

    private final String coordinates;

    public Artifact(String groupId, String artifactId, String version, String extension, String classifier) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.extension = selectExtension(trimStart(extension, '.'), classifier);
        this.classifier = classifier;
        this.coordinates = groupId + ':' + artifactId + ':' + version;
    }

    public Artifact(String groupId, String artifactId, String version) {
        this(groupId, artifactId, version, "jar", "");
    }

    public Artifact(String groupId, String artifactId, String version, String extension) {
        this(groupId, artifactId, version, extension, "");
    }

    public Artifact(String groupId, String artifactId) {
        this(groupId, artifactId, "", "", "");
    }

    public Artifact mergeWith(Artifact other) {
        return new Artifact(firstNonBlank(groupId, other.groupId),
                firstNonBlank(artifactId, other.artifactId),
                firstNonBlank(version, other.version),
                firstNonBlank(extension, other.extension),
                firstNonBlank(classifier, other.classifier));
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
        return new Artifact(groupId, artifactId, version, ext, classifier);
    }

    public Artifact withVersion(Version ver) {
        var verString = ver.toString();
        if (verString.equals(version)) {
            return this;
        }
        return new Artifact(groupId, artifactId, verString, extension, classifier);
    }

    public Artifact withClassifier(String newClassifier) {
        if (classifier.equals(newClassifier)) {
            return this;
        }
        return new Artifact(groupId, artifactId, version, extension, newClassifier);
    }

    public static Artifact parseCoordinates(String artifact) {
        var coordinates = artifact.split(":", -1);
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
            case 5:
                return new Artifact(groupId, artifactId, coordinates[2], coordinates[3], coordinates[4]);
            default:
                throw new JBuildException("Cannot parse coordinates, expected 2 to 5 parts" +
                        " (groupId:artifactId[:version[:extension[:classifier]]]), found " +
                        coordinates.length + " part(s) in '" + artifact + "'",
                        USER_INPUT);
        }
    }

    /**
     * @return a standard file name for this artifact (artifactId[-version][-classifier].extension)
     */
    public String toFileName() {
        return artifactId + (version.isBlank() ? "" : "-" + version) +
                (classifier.isBlank() ? "" : "-" + classifier) + "." + extension;
    }

    @Override
    public String toString() {
        return "Artifact{" + getCoordinates() + (classifier.isBlank() ? "" : "-" + classifier) + ':' + extension + '}';
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
        if (!classifier.equals(artifact.classifier)) return false;
        return extension.equals(artifact.extension);
    }

    @Override
    public int hashCode() {
        int result = groupId.hashCode();
        result = 31 * result + artifactId.hashCode();
        result = 31 * result + version.hashCode();
        result = 31 * result + classifier.hashCode();
        result = 31 * result + extension.hashCode();
        return result;
    }

    @Override
    public void addTo(Element element, Document document) {
        addTo(element, document, true);
    }

    public void addTo(Element element, Document document, boolean includePackaging) {
        element.appendChild(document.createElement("groupId")).setTextContent(groupId);
        element.appendChild(document.createElement("artifactId")).setTextContent(artifactId);
        element.appendChild(document.createElement("version")).setTextContent(version);
        if (includePackaging) {
            var packaging = DependencyType.fromClassifier(classifier).string();
            element.appendChild(document.createElement("packaging")).setTextContent(packaging);
        }
    }

    private static String selectExtension(String selectedExtension, String classifier) {
        if (selectedExtension.isBlank()) {
            return DependencyType.fromClassifier(classifier).getExtension();
        }
        return selectedExtension;
    }

}
