package jbuild.artifact;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class ResolvedArtifact {
    private byte[] contents;
    public final long contentLength;
    public final Artifact artifact;
    public final ArtifactRetriever<?> retriever;

    public ResolvedArtifact(byte[] contents, Artifact artifact, ArtifactRetriever<?> retriever) {
        this.contents = contents;
        this.contentLength = contents.length;
        this.artifact = artifact;
        this.retriever = retriever;
    }

    @Override
    public String toString() {
        return "ResolvedArtifact{" +
                "content-length=" + contentLength +
                ", artifact=" + artifact +
                ", retriever=" + retriever.getDescription() +
                '}';
    }

    public void consumeContents(OutputStream writer) throws IOException {
        var c = contents;
        if (c == null) {
            throw new IllegalStateException("artifact contents already consumed: " + artifact);
        }
        writer.write(c);
        contents = null;
    }

    public InputStream consumeContents() {
        var c = contents;
        if (c == null) {
            throw new IllegalStateException("artifact contents already consumed: " + artifact);
        }
        contents = null;
        return new ByteArrayInputStream(c);
    }
}
