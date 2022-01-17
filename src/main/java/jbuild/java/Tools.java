package jbuild.java;

import jbuild.errors.JBuildException;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.errors.JBuildException.ErrorCause.IO_READ;
import static jbuild.errors.JBuildException.ErrorCause.IO_WRITE;
import static jbuild.util.TextUtils.LINE_END;

public abstract class Tools {

    private final Streams streams;
    protected final ToolProvider tool;

    private Tools(ToolProvider tool,
                  Streams streams) {
        this.tool = tool;
        this.streams = streams;
    }

    /**
     * Lookup a Java tool by name.
     *
     * @param tool name
     * @return the tool
     * @throws JBuildException if the tool cannot be found
     */
    public static ToolProvider lookupTool(String tool) {
        return ToolProvider.findFirst(tool)
                .orElseThrow(() -> new JBuildException(tool + " is not available in this JVM, cannot run fix command." +
                        LINE_END + "Consider using a full JDK installation to run jbuild.", ACTION_ERROR));
    }

    /**
     * Verify that a tool has run successfully (exited with code 0).
     *
     * @param tool   name of tool
     * @param result tool run result
     * @throws JBuildException if the tool did not run successfully
     */
    public static void verifyToolSuccessful(String tool, ToolRunResult result) {
        if (result.exitCode() != 0) {
            throw new JBuildException("unexpected error when executing " + tool + " " + Arrays.toString(result.getArgs()) +
                    ". Tool output:" + LINE_END + result.getStdout() +
                    LINE_END + LINE_END + "stderr:" + LINE_END + result.getStderr(), ACTION_ERROR);
        }
    }

    protected PrintStream stdout() {
        return streams.stdout();
    }

    protected PrintStream stderr() {
        return streams.stderr();
    }

    protected ToolRunResult result(int exitCode, String[] args) {
        return streams.result(exitCode, args);
    }

    public static final class Javap extends Tools {

        private static final ToolProvider toolProvider = Tools.lookupTool("javap");

        public static Javap create() {
            return new Javap(toolProvider, new MemoryStreams());
        }

        public static Javap createFileBacked() {
            Path stdout, stderr;
            try {
                stdout = Files.createTempFile(Javap.class.getName(), ".txt");
                stderr = Files.createTempFile(Javap.class.getName(), ".txt");
            } catch (IOException e) {
                throw new JBuildException("Cannot create temp files for javap stdout/stderr", IO_WRITE);
            }
            return new Javap(toolProvider, new FileStreams(stdout.toFile(), stderr.toFile()));
        }

        private Javap(ToolProvider tool, Streams streams) {
            super(tool, streams);
        }

        public ToolRunResult run(String jarPath, Collection<String> classNames) {
            var args = collectArgs(jarPath, classNames);
            var exitCode = tool.run(stdout(), stderr(), args);
            return result(exitCode, args);
        }

        private static String[] collectArgs(String jarPath, Collection<String> classNames) {
            var result = new String[classNames.size() + 6];
            result[0] = "-v";
            result[1] = "-s";
            result[2] = "-c";
            result[3] = "-p";
            result[4] = "-classpath";
            result[5] = jarPath;
            var i = 6;
            for (var className : classNames) {
                result[i] = className;
                i++;
            }
            return result;
        }

    }

    public static final class Jar extends Tools {

        private static final ToolProvider toolProvider = Tools.lookupTool("jar");

        public static Jar create() {
            return new Jar(toolProvider);
        }

        private Jar(ToolProvider tool) {
            super(tool, new MemoryStreams());
        }

        public ToolRunResult listContents(String jarPath) {
            var exitCode = tool.run(
                    new PrintStream(stdout(), false, ISO_8859_1),
                    new PrintStream(stderr(), false, ISO_8859_1),
                    "tf", jarPath);
            return result(exitCode, new String[]{"tf", jarPath});
        }

    }

    private interface Streams {
        PrintStream stdout();

        PrintStream stderr();

        String readStdout();

        Stream<String> readStdoutLines();

        String readStderr();

        Stream<String> readStderrLines();

        ToolRunResult result(int exitCode, String[] args);
    }

    private static final class MemoryStreams implements Streams {
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

    private static final class FileStreams implements Streams {
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

    private static final class StreamsToolResult implements ToolRunResult {

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
}
