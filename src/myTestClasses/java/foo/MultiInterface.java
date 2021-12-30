package foo;

import java.io.Closeable;

public interface MultiInterface extends Runnable, Closeable, AutoCloseable {
}
