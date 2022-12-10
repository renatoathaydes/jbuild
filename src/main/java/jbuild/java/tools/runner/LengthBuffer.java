package jbuild.java.tools.runner;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import jbuild.java.tools.runner.LengthBuffer;

final class LengthBuffer {
    private final ByteBuffer lengthBuffer = ByteBuffer.allocate(4);

    public LengthBuffer() {
        lengthBuffer.mark();
    }

    void put(int index, int b) {
        assert 0 <= index && index < 4;
        lengthBuffer.put(index, (byte) b);
    }

    int getInt() {
        var result = lengthBuffer.getInt();
        lengthBuffer.reset();
        return result;
    }

    public void write(int size, OutputStream out) throws IOException {
        lengthBuffer.putInt(0, size);
        out.write(lengthBuffer.array());
    }
}
