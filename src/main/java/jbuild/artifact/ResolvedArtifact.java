package jbuild.artifact;

public final class ResolvedArtifact {
    public final byte[] contents;
    public final Artifact artifact;

    public ResolvedArtifact(byte[] contents, Artifact artifact) {
        this.contents = contents;
        this.artifact = artifact;
    }

    @Override
    public String toString() {
        return "ResolvedArtifact{" +
                "content-length=" + contents.length +
                ", artifact=" + artifact +
                '}';
    }
}
