package jbuild.api;

import jbuild.api.change.ChangeSet;

import java.io.IOException;
import java.util.List;

/**
 * A jb build task.
 * <p>
 * <h3>Implementation requirements</h3>
 * Implementations must be annotated with {@link JbTaskInfo}.
 * If a task declares that it returns any {@link JbTask#outputs()}, then it must produce those outputs.
 * A task should not read any files except those declared in {@link JbTask#inputs()},
 * or start any Threads which do not complete before the task's run method returns.
 * <p>
 * <h3>Instantiation and run rules</h3>
 * A task is instantiated if it's invoked directly or indirectly (via task transitive dependencies),
 * or when jb must report task metadata (inputs/outputs), for example, when the user invokes jb with the
 * {@code -s -l debug} flags.
 * A task will not be instantiated more than once per build. A task may or may not be executed after being instantiated.
 * When the task is executed, it is only executed once, and only one of the {@code run} methods is invoked
 * (on incremental builds, the {@link JbTask#run(ChangeSet, String...)} method is called, otherwise
 * {@link JbTask#run(String...)} is invoked).
 * <p>
 * Notice that tasks do not need to support incremental builds. Only implement {@link JbTask#run(ChangeSet, String...)}
 * if you can support incremental execution (the default implementation delegates to {@link JbTask#run(String...)}).
 * <p>
 * <h3>Task Configuration</h3>
 * If a task implementation provides a default constructor, then {@code jb} will call that as
 * long as no configuration is provided for the task.
 * <p>
 * Configurable task implementations may take arguments in its constructors, so {@code jb}
 * will try to match the configuration provided for the task with the arguments of the best matching constructor.
 * <p>
 * Each provided configuration parameter must match an argument of one of the constructors by name and type.
 * <p>
 * To find configuration for the task in the {@code jb} configuration, a top-level property
 * with the same name as the task is searched for.
 * <p>
 * The following types may be used by an argument of a task constructor:
 * <ul>
 *     <li>{@link JBuildLogger} (provided by {@code jb})</li>
 *     <li>{@link jbuild.api.config.JbConfig} (provided by {@code jb})</li>
 *     <li>{@link String} (may be null)</li>
 *     <li>{@code int}</li>
 *     <li>{@code float}</li>
 *     <li>{@code boolean}</li>
 *     <li>{@code List<String>} (may be null)</li>
 *     <li>{@code String[]} (may be null)</li>
 * </ul>
 * For example, if the task is called {@code example-task},
 * then the configuration for the task may look like this in the {@code jb} configuration file:
 * <pre>
 * <code>
 * example-task:
 *     quiet: false
 * </code>
 * </pre>
 * This would match a task constructor that looks as follows:
 * <pre>
 * <code>
 * {@code public ExampleTask(boolean quiet)}
 * </code>
 * <h4>How jb chooses a constructor to call</h4>
 * Which constructor is chosen depends on the data provided by the task configuration.
 * The more arguments a constructor takes, the higher its priority.
 * If the provided parameters are not enough to call any constructors, jb will attempt to use the
 * constructor with the fewest parameters, passing {@code null} to the missing arguments.
 * <h4>Receiving jb's own configuration</h4>
 * A task data may receive {@code jb}'s own configuration by using a constructor parameter with
 * the {@link jbuild.api.config.JbConfig} type.
 * <p>
 * For example:
 * <pre>
 * <code>
 * {@code
 * ExampleTask(JbConfig jbConfig) {}}
 * </code>
 * </pre>
 * Notice that jb configuration will not include custom task's configurations.
 */
public interface JbTask {

    /**
     * Get the task inputs.
     * <p>
     * Inputs may be file entities paths or simple patterns. Paths ending with a
     * {@code /} are directories. Patterns may be of the simple form {@code *.txt}
     * to match files by extension on a particular directory, or
     * {@code **&#47;some-dir/*.txt} to also include files in subdirectories.
     *
     * @return input paths and patterns
     */
    default List<String> inputs() {
        return List.of();
    }

    /**
     * Get the task outputs.
     * <p>
     * Outputs may be file entities paths or simple patterns. Paths ending with a
     * {@code /} are directories. Patterns may be of the simple form {@code *.txt}
     * to match files by extension on a particular directory, or
     * {@code **&#47;some-dir/*.txt} to also include files in subdirectories.
     *
     * @return output paths and patterns
     */
    default List<String> outputs() {
        return List.of();
    }

    /**
     * @return tasks this task depends on.
     */
    default List<String> dependsOn() {
        return List.of();
    }

    /**
     * @return tasks that should depend on this task.
     */
    default List<String> dependents() {
        return List.of();
    }

    /**
     * Run this task.
     * <p>
     * If this task returns any {@link JbTask#inputs()} or {@link JbTask#outputs()},
     * then it will only be invoked in case any of those changed since the last invocation.
     * The detected changes are provided via the {@code changeSet} argument.
     * <p>
     * The task must produce the outputs it declared, otherwise {@code jb} will assume that
     * the task has failed.
     * <p>
     * To signal an error without causing jb to print the full stack-trace, throw
     * either {@link IOException} or {@link JBuildException}. Any other
     * {@link Throwable} is considered a programmer error and will result in the
     * stack-trace being printed.
     * <p>
     * This method is only invoked on incremental builds. The initial build, or builds with the
     * cache disabled, will invoke {@link JbTask#run(String...)} instead.
     *
     * @param changeSet the changes since the last invocation of this task
     * @param args      command-line arguments provided by the user
     * @throws IOException if an IO errors occur
     * @implNote the default implementation just delegates to {@link JbTask#run(String...)}, so if you
     * do not need to support incremental builds, implement only that method.
     */
    default void run(ChangeSet changeSet, String... args) throws IOException {
        run(args);
    }

    /**
     * Run this task.
     * <p>
     * If this task returns any {@link JbTask#inputs()} or {@link JbTask#outputs()},
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
