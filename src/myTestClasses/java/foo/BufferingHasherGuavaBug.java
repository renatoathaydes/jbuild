package foo;

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;

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

    private GroovyBug groovyBug;

    public final class BufferingHasher {
        ExposedByteArrayOutputStream stream;

        public BufferingHasher putBytes(byte[] bytes, int off, int len) {
            stream.write(bytes, off, len);
            return this;
        }
    }

    abstract class GroovyBug extends URLClassLoader {
        public GroovyBug(URL[] urls) {
            super(urls);
        }

        public Class defineClass(String name, byte[] data) {
            return super.defineClass(name, data, 0, data.length);
        }
    }
}

class ExposedByteArrayOutputStream extends ByteArrayOutputStream {

    public void use(ExtendsAbstractMulti multi) {
        // calling interface method via abstract class
        multi.putAll(Map.of("yes", true));

        // calling default interface method
        multi.getOrDefault("something", false);
    }
}
