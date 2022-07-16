package jbuild.java.tools;

import jbuild.errors.JBuildException;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.spi.ToolProvider;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.util.TextUtils.LINE_END;

/**
 * Abstraction for Java tools provided by {@link ToolProvider}.
 */
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

    /**
     * The javap tool.
     */
    public static final class Javap extends Tools {

        private static final ToolProvider toolProvider = Tools.lookupTool("javap");

        public static Javap create() {
            return new Javap(toolProvider, new MemoryStreams());
        }

        private Javap(ToolProvider tool, Streams streams) {
            super(tool, streams);
        }

        /**
         * Run javap with the default flags used in JBuild.
         * <p>
         * The output can be piped into {@link jbuild.java.JavapOutputParser} for parsing.
         *
         * @param classpath  the classpath. May be empty.
         * @param classNames name of types to include
         * @return result
         */
        public ToolRunResult run(String classpath, Collection<String> classNames) {
            var args = collectArgs(classpath, classNames);
            var exitCode = tool.run(stdout(), stderr(), args);
            return result(exitCode, args);
        }

        private static String[] collectArgs(String classpath,
                                            Collection<String> classNames) {
            var extraArgs = classpath.isBlank() ? 4 : 6;
            var result = new String[classNames.size() + extraArgs];
            var i = 0;
            result[i++] = "-v";
            result[i++] = "-s";
            result[i++] = "-c";
            result[i++] = "-p";
            if (!classpath.isBlank()) {
                result[i++] = "-classpath";
                result[i++] = classpath;
            }
            for (var className : classNames) {
                result[i++] = className;
            }
            return result;
        }

    }

    /**
     * The jar tool.
     */
    public static final class Jar extends Tools {

        private static final ToolProvider toolProvider = Tools.lookupTool("jar");

        public static Jar create() {
            return new Jar(toolProvider);
        }

        private Jar(ToolProvider tool) {
            super(tool, new MemoryStreams());
        }

        /**
         * Run the jar tool in order to list the contents of the jar file.
         *
         * @param jarPath path to the jar
         * @return result
         */
        public ToolRunResult listContents(String jarPath) {
            var exitCode = tool.run(
                    new PrintStream(stdout(), false, ISO_8859_1),
                    new PrintStream(stderr(), false, ISO_8859_1),
                    "tf", jarPath);
            return result(exitCode, new String[]{"tf", jarPath});
        }

        /**
         * Create a jar according to the provided options.
         *
         * @param options create jar options
         * @return result
         */
        public ToolRunResult createJar(CreateJarOptions options) {
            var args = options.toArgs().toArray(new String[0]);
            var exitCode = tool.run(new PrintStream(stdout(), false, ISO_8859_1),
                    new PrintStream(stderr(), false, ISO_8859_1),
                    args);
            return result(exitCode, args);
        }

    }

    public static final class Javac extends Tools {

        private static final ToolProvider toolProvider = lookupTool("javac");

        public static Javac create() {
            return new Javac(toolProvider);
        }

        private Javac(ToolProvider tool) {
            super(tool, new MemoryStreams());
        }

        /**
         * Run the javac tool in order to compile all given files.
         *
         * @param files     files to compile
         * @param outDir    where to store compiled class files
         * @param classpath the classpath (may be empty)
         * @return result
         */
        public ToolRunResult compile(Set<String> files,
                                     String outDir,
                                     String classpath) {
            var args = collectArgs(files, outDir, classpath);
            var exitCode = tool.run(
                    new PrintStream(stdout(), false, ISO_8859_1),
                    new PrintStream(stderr(), false, ISO_8859_1),
                    args);
            return result(exitCode, args);
        }

        private static String[] collectArgs(Set<String> files,
                                            String outDir,
                                            String classpath) {
            var extraArgs = classpath.isBlank() ? 2 : 4;
            var result = new String[files.size() + extraArgs];
            var i = 0;
            result[i++] = "-d";
            result[i++] = outDir;
            if (!classpath.isBlank()) {
                result[i++] = "-cp";
                result[i++] = classpath;
            }
            for (var file : files) {
                result[i++] = file;
            }
            return result;
        }
    }

}
