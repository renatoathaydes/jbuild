package jbuild.script;

import javax.script.Bindings;
import java.util.Set;

public interface JBuildLangProvider {

    Set<String> extensions();

    String language();

    /**
     * @return a function that invokes the JBuild install command.
     * It must accept a (String... artifacts) argument.
     * The {@link #getInstallDependencies(Bindings)} method is used to extract dependencies
     * added by calling this function.
     */
    Object installFunction();

    /**
     * Extract the dependencies to be installed.
     *
     * @param bindings script engine bindings
     * @return dependencies added by calling {@link #installFunction()}.
     */
    Set<String> getInstallDependencies(Bindings bindings);
}
