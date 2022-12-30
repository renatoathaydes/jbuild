package jbuild.classes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class ByteScanner {
    private final ByteBuffer buffer;

    private int latestBytesRead;

    public ByteScanner(byte[] bytes) {
        buffer = ByteBuffer.wrap(bytes);
        buffer.position(0);
        buffer.order(ByteOrder.BIG_ENDIAN);
    }

    public ByteScanner(InputStream stream) throws IOException {
        this(stream.readAllBytes());
    }

    public int previousPosition() {
        return buffer.position() - latestBytesRead;
    }

    public int nextInt() {
        latestBytesRead = 4;
        return buffer.getInt();
    }

    public float nextFloat() {
        latestBytesRead = 4;
        return buffer.getFloat();
    }

    public short nextShort() {
        latestBytesRead = 2;
        return buffer.getShort();
    }

    public long nextLong() {
        latestBytesRead = 8;
        return buffer.getLong();
    }

    public double nextDouble() {
        latestBytesRead = 8;
        return buffer.getDouble();
    }

    public byte nextByte() {
        latestBytesRead = 1;
        return buffer.get();
    }

    public void next(byte[] contents) {
        latestBytesRead = contents.length;
        buffer.get(contents);
    }
}
