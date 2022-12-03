package jbuild.java.tools.runner;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * An {@link InputStream} implementation that breaks up the delegate's stream into chunks.
 * <p>
 * Users of this stream will get a chunk at a time. After each chunk, it will look like this stream has no more data,
 * but attempting to read again will start the next chunk, if any.
 * <p>
 * Each chunk starts with 4 bytes which determine the length of the next chunk. A chunk with length zero determines
 * the last chunk, after which no more data will be read from the delegate stream.
 */
public final class ChunkedInputStream extends FilterInputStream {
    private final ByteBuffer lengthBuffer = ByteBuffer.allocate(4);

    // if null, a new chunk can be started on the next read operation.
    // if zero length, no more data should be read from the delegate.
    private byte[] nextChunk;

    // current index within the nextChunk
    private int index;

    public ChunkedInputStream(InputStream delegate) {
        super(delegate);
        lengthBuffer.mark();
    }

    @Override
    public int read() throws IOException {
        if (nextChunk == null) {
            consumeChunk();
        }
        if (nextChunk.length == 0) return -1;
        if (index < nextChunk.length) {
            return nextChunk[index++];
        }
        return -1;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (nextChunk == null) {
            if (!nextChunk()) {
                return -1;
            }
        }
        var remainingBytes = Math.min(nextChunk.length - index, len);
        if (remainingBytes == 0) return -1;
        System.arraycopy(nextChunk, index, b, off, remainingBytes);
        index += remainingBytes;
        return remainingBytes;
    }

    private void consumeChunk() throws IOException {
        for (var i = 0; i < 4; i++) {
            var b = super.read();
            if (b < 0) {
                // never try to start chunk again
                nextChunk = new byte[0];
                return;
            }
            lengthBuffer.put(i, (byte) b);
        }
        var length = lengthBuffer.getInt();
        lengthBuffer.reset();
        if (length <= 0) {
            nextChunk = new byte[0];
            return;
        }
        nextChunk = new byte[length];
        for (var i = 0; i < length; i++) {
            var b = super.read();
            if (b < 0) {
                nextChunk = new byte[0];
                throw new IllegalStateException("Unexpected EOF while reading data for a chunk");
            }
            nextChunk[i] = (byte) b;
        }
        index = 0;
    }

    /**
     * Move to the next chunk if there exists one.
     *
     * @return true if there is another chunk
     */
    public boolean nextChunk() throws IOException {
        if (nextChunk != null && nextChunk.length == 0) return false;
        consumeChunk();
        return nextChunk.length > 0;
    }

    /**
     * This method does nothing.
     */
    @Override
    public void close() {
        // never close the delegate
    }
}
