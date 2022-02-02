package jbuild.java.tools;

import jbuild.errors.JBuildException;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static jbuild.errors.JBuildException.ErrorCause.IO_READ;
import static jbuild.errors.JBuildException.ErrorCause.IO_WRITE;

final class FileStreams implements Streams {
    private final File out;
    private final File err;

    public FileStreams(File stdout, File stderr) {
        out = stdout;
        err = stderr;
    }

    @Override
    public PrintStream stdout() {
        try {
            return new PrintStream(
                    new BufferedOutputStream(new FileOutputStream(out), 1024 * 64),
                    false, ISO_8859_1);
        } catch (FileNotFoundException e) {
            throw new JBuildException("Cannot write stdout to file as it does not exist: " + out, IO_WRITE);
        }
    }

    @Override
    public PrintStream stderr() {
        try {
            return new PrintStream(new BufferedOutputStream(
                    new FileOutputStream(err), 1024),
                    false, ISO_8859_1);
        } catch (FileNotFoundException e) {
            throw new JBuildException("Cannot write stderr to file as it does not exist: " + err, IO_WRITE);
        }
    }

    @Override
    public String readStdout() {
        try {
            return Files.readString(out.toPath(), ISO_8859_1);
        } catch (IOException e) {
            throw new JBuildException("Cannot read stdout file: " + e, IO_READ);
        }
    }

    @Override
    public String readStderr() {
        try {
            return Files.readString(err.toPath(), ISO_8859_1);
        } catch (IOException e) {
            throw new JBuildException("Cannot read stderr file: " + e, IO_READ);
        }
    }

    @Override
    public Stream<String> readStdoutLines() {
        try {
            return Files.lines(out.toPath(), ISO_8859_1);
        } catch (IOException e) {
            throw new JBuildException("Cannot read stdout file: " + e, IO_READ);
        }
    }

    @Override
    public Stream<String> readStderrLines() {
        try {
            return Files.lines(err.toPath(), ISO_8859_1);
        } catch (IOException e) {
            throw new JBuildException("Cannot read stderr file: " + e, IO_READ);
        }
    }

    @Override
    public ToolRunResult result(int exitCode, String[] args) {
        return new StreamsToolResult(exitCode, args, this);
    }
}
