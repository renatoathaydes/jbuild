package jbuild.java;

import java.util.stream.Stream;

public final class MemoryToolRunResult implements ToolRunResult {

    public final int exitCode;
    private final String[] args;
    private final String stdout;
    private final String stderr;

    public MemoryToolRunResult(int exitCode, String[] args, String stdout, String stderr) {
        this.exitCode = exitCode;
        this.args = args;
        this.stderr = stderr;
        this.stdout = stdout;
    }

    @Override
    public int exitCode() {
        return exitCode;
    }

    @Override
    public String[] getArgs() {
        return args;
    }

    @Override
    public String getStdout() {
        return stdout;
    }

    @Override
    public Stream<String> getStdoutLines() {
        return stdout.lines();
    }

    @Override
    public String getStderr() {
        return stderr;
    }

    @Override
    public Stream<String> getStderrLines() {
        return stderr.lines();
    }
}
