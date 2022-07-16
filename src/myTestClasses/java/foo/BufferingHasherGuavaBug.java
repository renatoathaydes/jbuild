package foo;

import java.io.ByteArrayOutputStream;

/**
 * ClassGraph had an issue due to a Guava method not being found:
 * <p>
 * {@code
 * com.google.common.hash.AbstractNonStreamingHashFunction$ExposedByteArrayOutputStream#write(byte[], int, int)::void not found, but referenced from:
 * - guava-31.0.1-jre.jar!com.google.common.hash.AbstractNonStreamingHashFunction$BufferingHasher -> putBytes(byte[], int, int)::com.google.common.hash.Hasher
 * }
 * This class helps reproduce the issue.
 */
public class BufferingHasherGuavaBug {

    public final class BufferingHasher {
        ExposedByteArrayOutputStream stream;

        public BufferingHasher putBytes(byte[] bytes, int off, int len) {
            stream.write(bytes, off, len);
            return this;
        }
    }
}

class ExposedByteArrayOutputStream extends ByteArrayOutputStream {

}