
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

}
