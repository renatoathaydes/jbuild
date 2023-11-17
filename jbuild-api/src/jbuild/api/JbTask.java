package jbuild.api;

import java.io.IOException;

/**
 * A jb build task.
 * <p>
 * <h3>Implementation requirements</h3>
 * Implementations must be annotated with {@link JbTaskInfo}.
 * If a task declares {@link JbTaskInfo#outputs()}, then it must produce those outputs.
 * A task should not read any files except those declared in {@link JbTaskInfo#inputs()},
 * or start any Threads which do not complete before the task's run method returns.
 * <p>
 * <h3>Task Configuration</h3>
 * Implementations may take arguments in one or more of its constructors, and {@code jb}
 * will try to match the configuration provided for the task with one of them.
 * The provided configuration must match exactly one of the constructors, both by
 * parameter name and type.
 * <p>
 * To find configuration for the task in the {@code jb} configuration, a top-level property
 * with the same name as the task is searched for.
 * <p>
 * The following types may be used in a task constructor:
 * <ul>
 *     <li>{@link JBuildLogger} (provided by {@code jb} if requested)</li>
 *     <li>{@link String} (may be null)</li>
 *     <li>{@code int}</li>
 *     <li>{@code float}</li>
 *     <li>{@code boolean}</li>
 *     <li>{@code List<String>}</li>
 *     <li>{@code String[]}</li>
 * </ul>
 * For example, if the task is called {@code example-task},
 * then the configuration for the task may look like this in the {@code jb} configuration file:
 * <pre>
 * <code>
 * example-task:
 *     quite: false
 * </code>
 * </pre>
 * This would match a constructor as follows:
 * <pre>
 * <code>
 * {@code public record ExampleTask(boolean quiet)}
 * </code>
 * </pre>
 * Notice that when using records, the field name determines the property name in the {@code jb} configuration.
 * With regular classes, it's the constructor's parameter names that get used, hence the class must have
 * been compiled with the javac {@code -p} option (which is the default with JBuild).
 * <p>
 * A task data may receive {@code jb}'s own configuration properties as well
 * by annotating the relevant parameters with {@link JbConfigProperty}.
 * <p>
 * For example, to get the project version, a configuration class like the following could
 * be used:
 * <pre>
 * <code>
 * {@code
 * public record ExampleTaskConfig(@JbConfigProperty version) {}}
 * </code>
 * </pre>
 * Only {@link String} parameters are allowed to be {@code null}. Values of all other acceptable
 * types are guaranteed to be non-null if the configuration was accepted.
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
