package jbuild.artifact;

public final class ResolvedArtifactChecksum {
    /**
     * The artifact for which a checksum may have been retrieved.
     */
    public final ResolvedArtifact artifact;
    /**
     * The checksum if it was resolved, or null otherwise.
     */
    public final ResolvedArtifact checksum;

    public ResolvedArtifactChecksum(ResolvedArtifact artifact, ResolvedArtifact checksum) {
        this.artifact = artifact;
        this.checksum = checksum;
    }
}
