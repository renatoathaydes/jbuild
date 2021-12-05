package jbuild.maven;

import jbuild.artifact.Artifact;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static jbuild.maven.MavenUtils.parseMavenTimestamp;
import static jbuild.util.XmlUtils.childNamed;
import static jbuild.util.XmlUtils.childrenNamed;
import static jbuild.util.XmlUtils.descendantOf;
import static jbuild.util.XmlUtils.textOf;

public final class MavenMetadata {

    private final Element metadata;

    public MavenMetadata(Document doc) {
        this.metadata = childNamed("metadata", doc).orElseThrow(() ->
                new IllegalArgumentException("Not a Maven metadata XML document"));
    }

    public Artifact getCoordinates() {
        var groupId = textOf(childNamed("groupId", metadata));
        var artifactId = textOf(childNamed("artifactId", metadata));
        return new Artifact(groupId, artifactId, "");
    }

    public Optional<Instant> getLastUpdated() {
        try {
            return Optional.ofNullable(parseMavenTimestamp(textOf(
                    descendantOf(metadata, "versioning", "lastUpdated"))));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public String getLatestVersion() {
        return textOf(descendantOf(metadata, "versioning", "latest"));
    }

    public String getReleaseVersion() {
        return textOf(descendantOf(metadata, "versioning", "release"));
    }

    public List<String> getVersions() {
        var versions = descendantOf(metadata, "versioning", "versions");
        if (versions.isEmpty()) {
            return List.of();
        }
        return childrenNamed("version", versions.get()).stream()
                .map(Node::getTextContent)
                .collect(toList());
    }

}
