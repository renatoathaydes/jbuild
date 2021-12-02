package jbuild.errors;

import java.io.IOException;

/**
 * An Exception thrown when closing a resource.
 */
public class CloseException extends RuntimeException {
    public CloseException(IOException cause) {
        super(cause);
    }
}
