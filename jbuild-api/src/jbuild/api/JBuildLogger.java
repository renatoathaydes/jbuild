package jbuild.api;

import java.util.function.Supplier;

/**
 * JBuild Logger.
 * <p>
 * Prefer to use this interface rather than to log to {@link System#out} or {@link System#err} directly
 * because {@code jb} can process messages and associate them with each running task, improving the user
 * understanding of where messages are coming from.
 */
public interface JBuildLogger {

    /**
     * @return whether logging is enabled.
     */
    boolean isEnabled();

    /**
     * @return whether verbose logging is enabled.
     */
    boolean isVerbose();

    /**
     * If the log is enabled, print the provided message followed by a new line.
     *
     * @param message to be logged
     */
    void println(CharSequence message);

    /**
     * If the log is enabled, print the provided message.
     *
     * @param message to be logged
     */
    void print(CharSequence message);

    /**
     * If the log is enabled, invoke the provided {@link Supplier} and then
     * log the object returned by it followed by a new line.
     *
     * @param messageGetter getter for a message to be logged
     */
    void println(Supplier<? extends CharSequence> messageGetter);

    /**
     * If the log is enabled, invoke the provided {@link Supplier} and then
     * log the object returned by it.
     *
     * @param messageGetter getter for a message to be logged
     */
    void print(Supplier<? extends CharSequence> messageGetter);

    /**
     * If the log is enabled, print the provided {@link Throwable}
     * (may include the stackTrace depending on the type).
     *
     * @param throwable to be logged
     */
    void print(Throwable throwable);

    /**
     * If verbose logging is enabled, print the provided message followed by a new line.
     *
     * @param message to be logged
     */
    void verbosePrintln(CharSequence message);

    /**
     * If verbose logging is enabled, invoke the provided {@link Supplier} and then
     * log the object returned by it followed by a new line.
     *
     * @param messageGetter getter for a message to be logged
     */
    void verbosePrintln(Supplier<? extends CharSequence> messageGetter);

}
