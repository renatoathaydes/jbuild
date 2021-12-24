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

        public ToolRunResult run(String jarPath, String className) {
            var exitCode = tool.run(new PrintStream(out), new PrintStream(err),
                    "-v", "-s", "-c", "-p", "-classpath", jarPath, className);
            return new ToolRunResult(exitCode, consumeOutput(out), consumeOutput(err));
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
