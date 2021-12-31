package jbuild.java;

import jbuild.errors.JBuildException;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.spi.ToolProvider;

import static java.nio.charset.StandardCharsets.UTF_8;
import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;

public abstract class Tools {

    protected final ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
    protected final ByteArrayOutputStream err = new ByteArrayOutputStream(1024);
    protected final ToolProvider tool;

    public Tools(ToolProvider tool) {
        this.tool = tool;
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
                .orElseThrow(() -> new JBuildException(tool + " is not available in this JVM, cannot run fix command.\n" +
                        "Consider using a full JDK installation to run jbuild.", ACTION_ERROR));
    }

    /**
     * Verify that a tool has run successfully (exited with code 0).
     *
     * @param tool   name of tool
     * @param result tool run result
     * @throws JBuildException if the tool did not run successfully
     */
    public static void verifyToolSuccessful(String tool, Tools.ToolRunResult result) {
        if (result.exitCode != 0) {
            throw new JBuildException("unexpected error when executing " + tool +
                    ". Tool output:\n" + result.stderr, ACTION_ERROR);
        }
    }

    private static String consumeOutput(ByteArrayOutputStream stream) {
        var output = stream.toString(UTF_8);
        stream.reset();
        return output;
    }

    public static final class Javap extends Tools {

        public static Javap create() {
            return new Javap(lookupTool("javap"));
        }

        private Javap(ToolProvider tool) {
            super(tool);
        }

        public ToolRunResult run(String jarPath, String... classNames) {
            var args = collectArgs(jarPath, classNames);
            var exitCode = tool.run(new PrintStream(out), new PrintStream(err), args);
            return new ToolRunResult(exitCode, consumeOutput(out), consumeOutput(err));
        }

        private static String[] collectArgs(String jarPath, String... classNames) {
            var result = new String[classNames.length + 6];
            result[0] = "-v";
            result[1] = "-s";
            result[2] = "-c";
            result[3] = "-p";
            result[4] = "-classpath";
            result[5] = jarPath;
            System.arraycopy(classNames, 0, result, 6, classNames.length);
            return result;
        }

    }

    public static final class Jar extends Tools {

        public static Jar create() {
            return new Jar(lookupTool("jar"));
        }

        private Jar(ToolProvider tool) {
            super(tool);
        }

        public ToolRunResult listContents(String jarPath) {
            var exitCode = tool.run(new PrintStream(out), new PrintStream(err),
                    "tf", jarPath);
            return new ToolRunResult(exitCode, consumeOutput(out), consumeOutput(err));
        }

    }

    public static final class ToolRunResult {

        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public ToolRunResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stderr = stderr;
            this.stdout = stdout;
        }
    }
}
