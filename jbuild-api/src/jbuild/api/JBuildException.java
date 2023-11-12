package jbuild.api;

/**
 * Non-programmer errors that may happen when running a jb task.
 * <p>
 * It allows providing a category of error with {@link ErrorCause}.
 * Exceptions of this type normally do not cause a stackTrace to be printed out
 * by the CLI.
 */
public class JBuildException extends RuntimeException {
    public enum ErrorCause {
        UNKNOWN, USER_INPUT, TIMEOUT, IO_READ, IO_WRITE, ACTION_ERROR
    }

    private final ErrorCause cause;

    public JBuildException(String reason, ErrorCause cause) {
        super(reason);
        this.cause = cause;
    }

    public ErrorCause getErrorCause() {
        return cause;
    }
}
