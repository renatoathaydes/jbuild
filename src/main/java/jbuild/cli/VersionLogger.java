package jbuild.cli;

import jbuild.artifact.Artifact;
import jbuild.log.JBuildLog;
import jbuild.maven.MavenMetadata;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

final class VersionLogger {

    private final JBuildLog log;

    public VersionLogger(JBuildLog log) {
        this.log = log;
    }

    void log(Artifact artifact, MavenMetadata mavenMetadata) {
        log.println("Versions of " + artifact.getCoordinates() + ":");

        var latest = mavenMetadata.getLatestVersion();
        var release = mavenMetadata.getReleaseVersion();
        var versions = mavenMetadata.getVersions();

        if (!latest.isBlank()) {
            log.println("  * Latest: " + latest);
        }
        if (!release.isBlank()) {
            log.println("  * Release: " + latest);
        }

        mavenMetadata.getLastUpdated().ifPresent(updated -> {
            log.println("  * Last updated: " + ZonedDateTime.ofInstant(updated, ZoneId.systemDefault())
                    .format(DateTimeFormatter.RFC_1123_DATE_TIME));
        });

        if (versions.isEmpty()) {
            log.println("  * no versions available");
        } else {
            log.println("  * All versions:");
            for (var version : versions) {
                log.println("    - " + version);
            }
        }
    }

}
