package jbuild.java.tools;

import java.util.stream.Stream;

public interface ToolRunResult {
    int exitCode();

    String[] getArgs();

    String getStdout();

    Stream<String> getStdoutLines();

    String getStderr();

    Stream<String> getStderrLines();
}
