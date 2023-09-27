package jbuild.api;

import java.io.IOException;

/**
 * A jb build task.
 */
public interface JbTask {

    /**
     * Run this task.
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
