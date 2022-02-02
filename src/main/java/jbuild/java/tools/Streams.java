package jbuild.java.tools;

import java.io.PrintStream;
import java.util.stream.Stream;

interface Streams {
    PrintStream stdout();

    PrintStream stderr();

    String readStdout();

    Stream<String> readStdoutLines();

    String readStderr();

    Stream<String> readStderrLines();

    ToolRunResult result(int exitCode, String[] args);
}
