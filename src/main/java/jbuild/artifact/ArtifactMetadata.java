package jbuild.artifact;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Metadata about an {@link Artifact}.
 */
public interface ArtifactMetadata {

    /**
     * @return artifact about which this metadata is about.
     */
    Artifact getCoordinates();

    /**
     * @return time the metadata has been last updated.
     */
    Optional<Instant> getLastUpdated();

    /**
     * @return the latest known version of an artifact. May return the empty String if unknown.
     */
    String getLatestVersion();

    /**
     * Known artifact versions.
     * <p>
     * This may differ depending on the source repository.
     *
     * @return known versions
     */
    List<String> getVersions();
}
