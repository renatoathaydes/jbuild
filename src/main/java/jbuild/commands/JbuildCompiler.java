package jbuild.commands;

import jbuild.java.tools.ToolRunResult;

import java.util.List;
import java.util.Set;

public interface JbuildCompiler {

    /**
     * Run the compiler tool in order to compile all given files.
     *
     * @param sourceFiles  files to compile
     * @param outDir       where to store compiled class files
     * @param classpath    the classpath (may be empty)
     * @param compilerArgs compiler arguments
     * @return result
     */
    ToolRunResult compile(Set<String> sourceFiles,
                          String outDir,
                          String classpath,
                          List<String> compilerArgs);
}
