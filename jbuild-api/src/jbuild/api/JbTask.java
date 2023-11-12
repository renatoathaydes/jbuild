package jbuild.api;

import java.io.IOException;

/**
 * A jb build task.
 * <p>
 * <h3>Implementation requirements</h3>
 * Implementations must be annotated with {@link JbTaskInfo}.
 * <p>
 * Implementations may take arguments in one or more of its constructors, and {@code jb}
 * will use the most appropriate one it can provide data for.
 * <p>
 * The following types may be provided by {@code jb}:
 * <ul>
 *     <li>{@link JBuildLogger}</li>
 *     <li>A configuration class or record (see below)</li>
 * </ul>
 * <p>
 * <h3>Task Configuration</h3>
 * A task may specify that it accepts configuration by declaring a public constructor which accepts
 * a configuration object.
 * A configuration object is an instance of a class which has one or more constructors that accept
 * named parameters with configuration data.
 * To create an instance of a configuration object, {@code jb} tries to find configuration for the
 * task in the {@code jb} configuration file under a property with the same name as the task.
 * <p>
 * For example, if the task is called {@code example-task},
 * then the configuration for the task may look like this in the {@code jb} configuration file:
 * <pre>
 * <code>
 * example-task:
 *     quite: false
 * </code>
 * </pre>
 * The configuration class or record for the above task would then look like this:
 * <pre>
 * <code>
 * {@code public record ExampleTaskConfig(
 *     boolean quiet) {}}
 * </code>
 * </pre>
 * Notice that when using records, the field name determines the property name in the {@code jb} configuration.
 * With regular classes, it's the constructor's parameter names that get used, hence the class must have
 * been compiled with the javac {@code -p} option (which is the default with JBuild).
 * <p>
 * The configuration object can receive {@code jb}'s own configuration properties as well
 * by annotating the relevant parameter with {@link JbConfigProperty}.
 * <p>
 * For example, to get the project version, a configuration class like the following could
 * be used:
 * <pre>
 * <code>
 * {@code
 * public record ExampleTaskConfig(
 *     @JbConfigProperty version) {}}
 * </code>
 * </pre>
 * If any property is optional, the configuration object can either use the type {@link java.util.Optional}
 * for it, or provide alternative constructors in which optional parameters do not appear.
 */
public interface JbTask {

    /**
     * Run this task.
     * <p>
     * If this task declared {@link JbTaskInfo#inputs()} and {@link JbTaskInfo#outputs()},
     * then it will only be invoked in case any of those changed.
     * <p>
     * The task must produce the outputs it declared, otherwise {@code jb} will assume that
     * the task has failed.
     * <p>
     * To signal an error without causing jb to print the full stack-trace, throw
     * either {@link IOException} or {@link JBuildException}. Any other
     * {@link Throwable} is considered a programmer error and will result in the
     * stack-trace being printed.
     *
     * @param args command-line arguments provided by the user
     * @throws IOException if an IO errors occur
     */
    void run(String... args) throws IOException;

}
