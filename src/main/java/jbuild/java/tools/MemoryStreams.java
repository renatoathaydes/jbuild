package jbuild.java.tools;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

final class MemoryStreams implements Streams {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
    private final ByteArrayOutputStream err = new ByteArrayOutputStream(1024);

    @Override
    public PrintStream stdout() {
        return new PrintStream(out, false, ISO_8859_1);
    }

    @Override
    public PrintStream stderr() {
        return new PrintStream(err, false, ISO_8859_1);
    }

    @Override
    public String readStdout() {
        return out.toString(ISO_8859_1);
    }

    @Override
    public String readStderr() {
        return err.toString(ISO_8859_1);
    }

    @Override
    public Stream<String> readStdoutLines() {
        return readStdout().lines();
    }

    @Override
    public Stream<String> readStderrLines() {
        return readStderr().lines();
    }

    @Override
    public ToolRunResult result(int exitCode, String[] args) {
        try {
            return new MemoryToolRunResult(exitCode, args, readStdout(), readStderr());
        } finally {
            out.reset();
            err.reset();
        }
    }
}
