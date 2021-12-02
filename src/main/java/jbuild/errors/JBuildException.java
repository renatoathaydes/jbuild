package jbuild.errors;

public class JBuildException extends RuntimeException {
    public enum ErrorCause {
        UNKNOWN, USER_INPUT, TIMEOUT, IO_READ, IO_WRITE
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
