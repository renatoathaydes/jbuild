package jbuild.classes;

public class ClassFileException extends RuntimeException {
    public final int offset;

    public ClassFileException(String message, int offset) {
        super(message);
        this.offset = offset;
    }
}