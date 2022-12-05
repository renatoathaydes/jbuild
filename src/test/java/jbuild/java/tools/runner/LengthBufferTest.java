package jbuild.java.tools.runner;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class LengthBufferTest {

    @Test
    void canReadLength() {
        var buffer = new LengthBuffer();
        buffer.put(0, 0x11);
        buffer.put(1, 0x22);
        buffer.put(2, 0x33);
        buffer.put(3, 0x44);
        assertThat(buffer.getInt()).isEqualTo(0x11223344);
    }

    @Test
    void canWriteFullInt() throws IOException {
        var buffer = new LengthBuffer();
        var out = new ByteArrayOutputStream();
        buffer.write(0x11223344, out);
        assertThat(out.toByteArray()).containsExactly(0x11, 0x22, 0x33, 0x44);
    }
}
