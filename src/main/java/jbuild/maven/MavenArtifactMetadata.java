package jbuild.maven;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactMetadata;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static jbuild.maven.MavenUtils.parseMavenTimestamp;
import static jbuild.util.XmlUtils.childNamed;
import static jbuild.util.XmlUtils.childrenNamed;
import static jbuild.util.XmlUtils.descendantOf;
import static jbuild.util.XmlUtils.textOf;

public final class MavenArtifactMetadata implements ArtifactMetadata {

    private final Element metadata;

    public MavenArtifactMetadata(Document doc) {
        this.metadata = childNamed("metadata", doc).orElseThrow(() ->
                new IllegalArgumentException("Not a Maven metadata XML document"));
    }

    @Override
    public Artifact getCoordinates() {
        var groupId = textOf(childNamed("groupId", metadata));
        var artifactId = textOf(childNamed("artifactId", metadata));
        return new Artifact(groupId, artifactId, "");
    }

    @Override
    public Optional<Instant> getLastUpdated() {
        try {
            return Optional.ofNullable(parseMavenTimestamp(textOf(
                    descendantOf(metadata, "versioning", "lastUpdated"))));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public String getLatestVersion() {
        return textOf(descendantOf(metadata, "versioning", "latest"));
    }

    public String getReleaseVersion() {
        return textOf(descendantOf(metadata, "versioning", "release"));
    }

    @Override
    public Set<String> getVersions() {
        var versions = descendantOf(metadata, "versioning", "versions");
        if (versions.isEmpty()) {
            return Set.of();
        }
        return childrenNamed("version", versions.get()).stream()
                .map(Node::getTextContent)
                .collect(toSet());
    }

    @Override
    public ArtifactMetadata merge(ArtifactMetadata other) {
        return new BasicArtifactMetadata(this).merge(other);
    }

}
