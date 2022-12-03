package jbuild.java.tools.runner;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class ChunkedInputStreamTest {

    @Test
    void canReadZeroBytes() throws IOException {
        byte[] data = new byte[]{0, 0, 0, 0};
        var stream = new ChunkedInputStream(new ByteArrayInputStream(data));
        assertThat(stream.read()).isEqualTo(-1);
        assertThat(stream.read()).isEqualTo(-1);
        assertThat(stream.nextChunk()).isFalse();
    }

    @Test
    void canReadOneByte() throws IOException {
        byte[] data = new byte[]{0, 0, 0, 1, (byte) 0xf3, 0, 0, 0, 0};
        var stream = new ChunkedInputStream(new ByteArrayInputStream(data));
        assertThat(stream.read()).isEqualTo((byte) 0xf3);
        assertThat(stream.read()).isEqualTo(-1);
        assertThat(stream.read()).isEqualTo(-1);
        assertThat(stream.nextChunk()).isFalse();
        assertThat(stream.read()).isEqualTo(-1);
    }

    @Test
    void canReadStringData() throws IOException {
        var string = "This is an example\nstring value!!".getBytes(UTF_8);
        var data = new byte[4 + string.length + 4];
        var bufferLength = ByteBuffer.allocate(4);
        bufferLength.putInt(data.length);
        System.arraycopy(bufferLength.array(), 0, data, 0, 4);
        System.arraycopy(string, 0, data, 4, string.length);

        var stream = new ChunkedInputStream(new ByteArrayInputStream(data));

        var firstChunk = stream.readAllBytes();
        assertThat(firstChunk).contains(string);
        assertThat(stream.read()).isEqualTo(-1);
        assertThat(stream.nextChunk()).isFalse();
    }

    @Test
    void canReadManyChunks() throws IOException {
        // chunks of length 300, then 10, then 0
        byte[] data = new byte[4 + 300 + 4 + 10 + 4];
        var len = ByteBuffer.allocate(4);
        len.mark();
        len.putInt(300);
        System.arraycopy(len.array(), 0, data, 0, 4);
        for (var i = 4; i < 304; i++) {
            data[i] = (byte) (0xff & i);
        }
        len.reset();
        len.putInt(10);
        System.arraycopy(len.array(), 0, data, 304, 4);
        for (var i = 308; i < 318; i++) {
            data[i] = (byte) 42;
        }
        for (var i = 318; i < 322; i++) {
            data[i] = (byte) 0;
        }
        var stream = new ChunkedInputStream(new ByteArrayInputStream(data));

        for (var i = 0; i < 300; i++) {
            assertThat(stream.read()).isEqualTo((byte) (0xff & (i + 4)));
        }

        assertThat(stream.read()).isEqualTo(-1);
        assertThat(stream.read()).isEqualTo(-1);
        assertThat(stream.nextChunk()).isTrue();

        for (var i = 0; i < 10; i++) {
            assertThat(stream.read()).isEqualTo((byte) 42);
        }

        assertThat(stream.read()).isEqualTo(-1);
        assertThat(stream.nextChunk()).isFalse();
        assertThat(stream.read()).isEqualTo(-1);
        assertThat(stream.read()).isEqualTo(-1);
    }

}
