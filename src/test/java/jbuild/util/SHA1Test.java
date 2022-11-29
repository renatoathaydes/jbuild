package jbuild.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

public class SHA1Test {

    @Test
    void sha1() {
        assertThat(SHA1.computeSha1("Hello JBuild!".getBytes(US_ASCII)))
                .containsExactly(new byte[]{
                        0x0f, (byte) 0xf9, 0x1f, (byte) 0xd0, (byte) 0xc7, (byte) 0xe5, 0x07, (byte) 0xdd, (byte) 0xf8,
                        (byte) 0x91, (byte) 0xa1, (byte) 0xd4, 0x49, 0x28, (byte) 0xa0, (byte) 0xc7, 0x0a, (byte) 0xcc,
                        0x3a, (byte) 0xbe});
    }

    @Test
    void fromSha1StringBytes() throws Exception {
        assertThat(
                SHA1.fromSha1StringBytes(readResource("/jbuild/maven/sha1/a-4.0.pom.sha1"))
        ).containsExactly(new byte[]{
                0x66, 0x09, 0x79, 0x1a, (byte) 0xa8, (byte) 0x9d, 0x48, 0x53, 0x3e, 0x10, (byte) 0xf2, 0x59,
                (byte) 0x90, (byte) 0xb3, 0x0d, 0x3d, (byte) 0xaa, (byte) 0xd9, 0x75, 0x09
        });
    }

    @Test
    void ensureSHA1ValidationWorks() throws Exception {
        var sha1 = SHA1.fromSha1StringBytes(readResource("/jbuild/maven/sha1/a-4.0.pom.sha1"));
        var computed = SHA1.computeSha1(readResource("/jbuild/maven/sha1/a-4.0.pom"));
        assertThat(computed).containsExactly(sha1);
    }

    private static byte[] readResource(String path) throws IOException {
        try (var stream = SHA1Test.class.getResourceAsStream(path)) {
            assert stream != null;
            return stream.readAllBytes();
        }
    }

}
