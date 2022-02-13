package jbuild.cli;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactMetadata;
import jbuild.artifact.Version;
import jbuild.log.JBuildLog;
import jbuild.maven.ArtifactKey;
import jbuild.maven.MavenArtifactMetadata;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

final class VersionLogger {

    private final JBuildLog log;

    public VersionLogger(JBuildLog log) {
        this.log = log;
    }

    synchronized void log(Artifact artifact, ArtifactMetadata artifactMetadata) {
        log.println("Versions of " + ArtifactKey.of(artifact).getCoordinates() + ":");

        var latest = artifactMetadata.getLatestVersion();
        var release = (artifactMetadata instanceof MavenArtifactMetadata)
                ? ((MavenArtifactMetadata) artifactMetadata).getReleaseVersion()
                : "";
        var versions = artifactMetadata.getVersions();

        if (!latest.isBlank()) {
            log.println("  * Latest: " + latest);
        }
        if (!release.isBlank()) {
            log.println("  * Release: " + latest);
        }

        artifactMetadata.getLastUpdated().ifPresent(updated -> {
            log.println("  * Last updated: " + ZonedDateTime.ofInstant(updated, ZoneId.systemDefault())
                    .format(DateTimeFormatter.RFC_1123_DATE_TIME));
        });

        if (versions.isEmpty()) {
            log.println("  * no versions available");
        } else {
            log.println("  * All versions:");
            versions.stream()
                    .map(Version::parse)
                    .sorted()
                    .forEach(version -> log.println("    - " + version));
        }
    }

}
