package jbuild.artifact;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;

/**
 * Resolved artifact.
 * <p>
 * Unlike most other JBuild classes, {@link ResolvedArtifact} is not completely immutable for memory
 * performance reasons. The contents of an artifact may be fairly large and keeping it around for longer
 * than necessary would cause more memory than necessary to be held by the JVM for no reason. For this
 * reason, it is advisable that the contents of a resolved artifact be <em>consumed</em> via one of the
 * consume methods, which can be called only once as after that, attempting to access the contents of this
 * artifact will result in an error.
 * <p>
 * To read the contents more than once, the {@link ResolvedArtifact#getContents()} getter may be used,
 * but that's only advisable in case it is known that another artifact handler will later consume it.
 */
public final class ResolvedArtifact {

    private byte[] contents;
    public final long contentLength;
    public final Artifact artifact;
    public final ArtifactRetriever<?> retriever;
    public final long requestTime;

    public ResolvedArtifact(byte[] contents,
                            Artifact artifact,
                            ArtifactRetriever<?> retriever,
                            long requestTime) {
        this.contents = contents;
        this.contentLength = contents.length;
        this.artifact = artifact;
        this.retriever = retriever;
        this.requestTime = requestTime;
    }

    @Override
    public String toString() {
        return "ResolvedArtifact{" +
                "content-length=" + contentLength +
                ", artifact=" + artifact +
                ", retriever=" + retriever.getDescription() +
                ", requestedAt=" + Instant.ofEpochMilli(requestTime) +
                '}';
    }

    /**
     * @return the resolved contents of this artifact.
     */
    public byte[] getContents() {
        var c = contents;
        if (c == null) {
            throw new IllegalStateException("artifact contents already consumed: " + artifact);
        }
        return c;
    }

    /**
     * Consume the contents of this artifact.
     * <p>
     * After this method is called, the contents of this artifact will be "forgotten" and attempting
     * to read its contents again will result in an {@link IllegalStateException} being thrown.
     *
     * @param writer into which to write the contents of this artifact.
     * @throws IOException if an error occurs while writing the artifact.
     */
    public void consumeContents(OutputStream writer) throws IOException {
        var c = getContents();
        writer.write(c);
        contents = null;
    }

    /**
     * Consume the contents of this artifact.
     * <p>
     * After this method is called, the contents of this artifact will be "forgotten" and attempting
     * to read its contents again will result in an {@link IllegalStateException} being thrown.
     *
     * @return an in-memory {@link InputStream} with the resolved contents of this artifact.
     */
    public InputStream consumeContents() {
        return new ByteArrayInputStream(consumeContentsToArray());
    }

    /**
     * Consume the contents of this artifact.
     * <p>
     * After this method is called, the contents of this artifact will be "forgotten" and attempting
     * to read its contents again will result in an {@link IllegalStateException} being thrown.
     *
     * @return the resolved contents of this artifact.
     */
    public byte[] consumeContentsToArray() {
        var c = getContents();
        contents = null;
        return c;
    }
}
