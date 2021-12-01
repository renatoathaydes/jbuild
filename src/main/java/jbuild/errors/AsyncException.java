package jbuild.errors;

public class AsyncException extends RuntimeException {
    public AsyncException(Exception cause) {
        super(cause);
    }
}
