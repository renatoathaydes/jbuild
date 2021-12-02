package jbuild.artifact;

import java.io.IOException;
import java.io.OutputStream;

public final class ResolvedArtifact {
    private byte[] contents;
    public final long contentLength;
    public final Artifact artifact;

    public ResolvedArtifact(byte[] contents, Artifact artifact) {
        this.contents = contents;
        this.contentLength = contents.length;
        this.artifact = artifact;
    }

    @Override
    public String toString() {
        return "ResolvedArtifact{" +
                "content-length=" + contentLength +
                ", artifact=" + artifact +
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
}
