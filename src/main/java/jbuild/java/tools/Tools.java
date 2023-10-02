package jbuild.java.tools;

import jbuild.api.JBuildException;
import jbuild.log.JBuildLog;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.spi.ToolProvider;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static jbuild.api.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.api.JBuildException.ErrorCause.USER_INPUT;
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
            throw new JBuildException("unexpected error when executing " + tool + " " +
                    argsString(result.getArgs()) +
                    ". Tool output:" + LINE_END + result.getStdout() +
                    LINE_END + LINE_END + "stderr:" + LINE_END + result.getStderr(), ACTION_ERROR);
        }
    }

    private static String argsString(String[] args) {
        if (args.length > 10) {
            var shortArgs = new String[10];
            System.arraycopy(args, 0, shortArgs, 0, 10);
            return String.join(" ", shortArgs) + "...";
        }
        return String.join(" ", args);
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

    protected ToolRunResult run(List<String> argList) {
        var args = argList.toArray(new String[0]);
        var exitCode = tool.run(new PrintStream(stdout(), false, ISO_8859_1),
                new PrintStream(stderr(), false, ISO_8859_1),
                args);
        return result(exitCode, args);
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
            var toolResult = tool.run(stdout(), stderr(), args);
            return result(toolResult, args);
        }

        /**
         * Run javap with the default flags used in JBuild.
         * <p>
         * The output can be piped into {@link jbuild.java.JavapOutputParser} for parsing.
         *
         * @param classFile the single class file to give to javap.
         * @return result
         */
        public ToolRunResult run(String classFile) {
            var args = collectArgs(classFile);
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

        private static String[] collectArgs(String classFile) {
            return new String[]{
                    "-v", "-s", "-c", "-p", classFile
            };
        }

    }

    /**
     * The javadoc tool.
     */
    public static final class Javadoc extends Tools {
        private static final ToolProvider toolProvider = Tools.lookupTool("javadoc");

        public static Javadoc create() {
            return new Javadoc(toolProvider);
        }

        private Javadoc(ToolProvider tool) {
            super(tool, new MemoryStreams());
        }

        /**
         * Create Javadoc.
         *
         * @param classpath   Java classpath
         * @param outputDir   destination directory
         * @param sourceFiles Java source files
         * @return result
         */
        public ToolRunResult createJavadoc(String classpath,
                                           Set<String> sourceFiles,
                                           String outputDir) {
            var args = new ArrayList<>(List.of("-cp", classpath, "-d", outputDir));
            args.addAll(sourceFiles);
            return run(args);
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
         * <p>
         * This method creates the "main" jar, with class files. For general-purpose jars
         * like sources and javadoc jars, use {@link Jar#createJar(String, Set)} instead.
         *
         * @param options create jar options
         * @return result
         */
        public ToolRunResult createJar(CreateJarOptions options) {
            return run(options.toArgs(true));
        }

        /**
         * Create an archive jar.
         *
         * @param jar  destination jar
         * @param dirs input directories
         * @return result
         */
        public ToolRunResult createJar(String jar, Set<String> dirs) {
            var args = new ArrayList<>(List.of("--create", "--file", jar));
            for (var sourceDir : dirs) {
                args.addAll(List.of("-C", sourceDir, "."));
            }
            return run(args);
        }

        /**
         * Update a jar by adding files to it.
         *
         * @param jarFile to update
         * @param fileSet files to add
         * @return result
         */
        public ToolRunResult updateJar(String jarFile,
                                       CreateJarOptions.FileSet fileSet) {
            var args = new ArrayList<String>();
            args.add("uf");
            args.add(jarFile);
            CreateJarOptions.addFileSetTo(args, fileSet);
            return run(args);
        }

    }

    public static final class Javac extends Tools {

        private static final ToolProvider toolProvider = lookupTool("javac");

        private final JBuildLog log;

        public static Javac create(JBuildLog log) {
            return new Javac(toolProvider, log);
        }

        private Javac(ToolProvider tool, JBuildLog log) {
            super(tool, new MemoryStreams());
            this.log = log;
        }

        /**
         * Run the javac tool in order to compile all given files.
         *
         * @param sourceFiles  files to compile
         * @param outDir       where to store compiled class files
         * @param classpath    the classpath (may be empty)
         * @param compilerArgs javac compiler arguments
         * @return result
         */
        public ToolRunResult compile(Set<String> sourceFiles,
                                     String outDir,
                                     String classpath,
                                     List<String> compilerArgs) {
            validateCompilerArgs(compilerArgs);
            var args = collectArgs(sourceFiles, outDir, classpath, compilerArgs);
            log.verbosePrintln(() -> "Compile command: javac " + String.join(" ", args));
            return run(args);
        }

        private void validateCompilerArgs(List<String> compilerArgs) {
            if (compilerArgs.contains("-s")) {
                throw new JBuildException("The -s javac option is not allowed, " +
                        "please use jbuild's -d (output directory) or -j (output jar) options instead.", USER_INPUT);
            }
            if (compilerArgs.contains("-h") || compilerArgs.contains("--help") || compilerArgs.contains("-?")) {
                throw new JBuildException("The javac help option is not allowed. Run javac --help instead.", USER_INPUT);
            }
            if (compilerArgs.contains("-version") || compilerArgs.contains("--version")) {
                throw new JBuildException("The javac version option is not allowed. Run javac -version instead.", USER_INPUT);
            }
        }

        private static List<String> collectArgs(Set<String> files,
                                                String outDir,
                                                String classpath,
                                                List<String> compilerArgs) {
            var result = new ArrayList<String>();

            if (!compilerArgs.contains("-encoding")) {
                result.add("-encoding");
                result.add("utf-8");
            }
            if (!compilerArgs.contains("-nowarn") && !compilerArgs.contains("-Werror")) {
                result.add("-Werror");
            }
            if (!compilerArgs.contains("-parameters")) {
                result.add("-parameters");
            }
            result.add("-d");
            result.add(outDir);

            if (!classpath.isBlank()) {
                result.add("-cp");
                result.add(classpath);
            }
            result.addAll(compilerArgs);
            result.addAll(files);

            return result;
        }
    }

}
