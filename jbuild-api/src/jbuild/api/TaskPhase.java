package jbuild.api;

/**
 * A jb build phase.
 * <p>
 * All jb tasks are executed on a phase. A phase contains a group of tasks which
 * may only run after tasks in the previous phase have finished executing,
 * regardless of task dependencies.
 * <p>
 * Built-in phases are {@code setup} (index 100), {@code build} (index 500),
 * {@code publish} (index 501) and {@code tearDown} (index 1000).
 */
public @interface TaskPhase {

    /**
     * @return name of the phase (may be an existing one or a custom phase)
     */
    String name();

    /**
     * The index of the phase. Phases are ordered according to this index.
     *
     * @return index of the phase (ignored if a phase with the provided name already exists).
     */
    int index() default -1;

}
