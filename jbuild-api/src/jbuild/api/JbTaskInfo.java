
package jbuild.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mandatory annotation for all implementations of {@link JbTask}.
 * <p>
 * The `jb` utility reads the information provided by this annotation and
 * generates a YAML metadata file during compilation. This file is then used at
 * runtime to efficiently find all tasks provided by a jar.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface JbTaskInfo {

    /**
     * @return task name
     */
    String name();

    /**
     * @return task description
     */
    String description() default "";

    /**
     * Get the task phase.
     * <p>
     * Built-in phases are {@code setup}, {@code build} and {@code tearDown}.
     * Use index {@code -1} for existing phases.
     *
     * @return task phase
     */
    TaskPhase phase() default @TaskPhase(name = "build");

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
    String[] inputs() default {};

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
    String[] outputs() default {};

    /**
     * @return tasks this task depends on.
     */
    String[] dependsOn() default {};

    /**
     * @return tasks that should depend on this task.
     */
    String[] dependents() default {};

}
