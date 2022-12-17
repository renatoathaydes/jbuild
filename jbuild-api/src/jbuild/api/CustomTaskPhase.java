package jbuild.api;

/**
 * A jb build phase.
 * <p>
 * All jb tasks are executed on a phase. A phase contains a group of tasks which
 * may only run after tasks in the previous phase have finished executing,
 * regardless of task dependencies.
 */
public @interface CustomTaskPhase {

    /**
     * The index of the phase. Phases are ordered according to this index.
     *
     * @return index of the phase
     */
    int index();

    /**
     * @return name of the phase
     */
    String name();
}
