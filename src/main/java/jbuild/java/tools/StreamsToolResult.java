package jbuild.java.tools;

import java.util.stream.Stream;

final class StreamsToolResult implements ToolRunResult {

    private final int exitCode;
    private final String[] args;
    private final Streams streams;

    public StreamsToolResult(int exitCode, String[] args, Streams streams) {
        this.exitCode = exitCode;
        this.args = args;
        this.streams = streams;
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
        return streams.readStdout();
    }

    @Override
    public Stream<String> getStdoutLines() {
        return streams.readStdoutLines();
    }

    @Override
    public String getStderr() {
        return streams.readStderr();
    }

    @Override
    public Stream<String> getStderrLines() {
        return streams.readStderrLines();
    }
}
