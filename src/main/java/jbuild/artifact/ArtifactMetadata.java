package jbuild.artifact;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toCollection;
import static jbuild.util.CollectionUtils.union;

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
    Set<String> getVersions();

    /**
     * Merge this with other.
     * <p>
     * Implementations may assume that both metadata instances refer to the same {@link Artifact}.
     *
     * @param other the other metadata
     * @return result of merging metadata constructively
     */
    ArtifactMetadata merge(ArtifactMetadata other);

    /**
     * Create a basic instance of {@link ArtifactMetadata} with the given characteristics.
     *
     * @param artifact      coordinates of the artifact
     * @param lastUpdated   optional lastUpdated time (may be null)
     * @param latestVersion the latest known version (may be empty)
     * @param versions      all known versions of the artifact
     * @return metadata for the given artifact
     */
    static ArtifactMetadata of(Artifact artifact,
                               Instant lastUpdated,
                               String latestVersion,
                               Set<String> versions) {
        return new BasicArtifactMetadata(artifact, lastUpdated, latestVersion, versions);
    }

    final class BasicArtifactMetadata implements ArtifactMetadata {

        private final Artifact artifact;
        private final Instant lastUpdated;
        private final String latestVersion;
        private final Set<String> versions;

        public BasicArtifactMetadata(Artifact artifact,
                                     Instant lastUpdated,
                                     String latestVersion,
                                     Set<String> versions) {
            this.artifact = artifact;
            this.lastUpdated = lastUpdated;
            this.latestVersion = latestVersion;
            this.versions = versions;
        }

        public BasicArtifactMetadata(ArtifactMetadata other) {
            this(other.getCoordinates(),
                    other.getLastUpdated().orElse(null),
                    other.getLatestVersion(),
                    other.getVersions());
        }

        @Override
        public Artifact getCoordinates() {
            return artifact;
        }

        @Override
        public Optional<Instant> getLastUpdated() {
            return Optional.ofNullable(lastUpdated);
        }

        @Override
        public String getLatestVersion() {
            return latestVersion;
        }

        @Override
        public Set<String> getVersions() {
            return versions;
        }

        @Override
        public ArtifactMetadata merge(ArtifactMetadata other) {
            var lastUpdated = mergeLastUpdated(this.lastUpdated, other.getLastUpdated().orElse(null));
            var latestVersion = mergeLatestVersion(this.latestVersion, other.getLatestVersion());
            var versions = union(this.versions, other.getVersions())
                    .stream().map(Version::parse)
                    .sorted()
                    .map(Version::toString)
                    .collect(toCollection(LinkedHashSet::new));
            return new BasicArtifactMetadata(artifact, lastUpdated, latestVersion, versions);
        }

        private String mergeLatestVersion(String version1, String version2) {
            var v1 = Version.parse(version1);
            var v2 = Version.parse(version2);
            if (v1.isAfter(v2)) return version1;
            return version2;
        }

        private static Instant mergeLastUpdated(Instant instant, Instant other) {
            if (instant == null) return other;
            if (other == null) return instant;
            return instant.isAfter(other) ? instant : other;
        }
    }
}
