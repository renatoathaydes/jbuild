package util;

import jbuild.api.JBuildException;
import jbuild.cli.Main;
import jbuild.java.tools.MemoryToolRunResult;
import jbuild.java.tools.ToolRunResult;
import jbuild.log.JBuildLog;
import jbuild.maven.MavenUtils;
import jbuild.util.FileUtils;
import org.junit.jupiter.api.BeforeAll;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static jbuild.api.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.util.TextUtils.LINE_END;
import static util.JBuildTestRunner.SystemProperties.integrationTestsRepo;

public class JBuildTestRunner {

    public static final String LE = System.lineSeparator();

    public interface Artifacts {
        String GUAVA = "com.google.guava:guava:31.0.1-jre";
        String APACHE_COMMONS_COMPRESS = "org.apache.commons:commons-compress:1.21";
        String JUNIT5_ENGINE = "org.junit.jupiter:junit-jupiter-engine:5.7.0";

        String GUAVA_JAR_NAME = "guava-31.0.1-jre.jar";

        String GROOVY_VERSION = "4.0.12";
        String GROOVY = "org.apache.groovy:groovy:" + GROOVY_VERSION;
        String GROOVYDOC_TOOL = "org.apache.groovy:groovy-groovydoc:" + GROOVY_VERSION;
        String GROOVY_JAR_NAME = "groovy-" + GROOVY_VERSION + ".jar";
    }

    public interface SystemProperties {
        File integrationTestsRepo = new File(System.getProperty("tests.int-tests.repo"));
    }

    static File getGroovydocToolDir() {
        return new File(integrationTestsRepo, "groovydoc-tool");
    }

    @BeforeAll
    static void initialize() {
        if (!integrationTestsRepo.isDirectory()) {
            System.out.println("Installing Maven repository for integration tests at " + integrationTestsRepo.getPath());
            var runner = new JBuildTestRunner();
            var result = runner.run("-r", MavenUtils.MAVEN_CENTRAL_URL, "install",
                    "-O", "-s", "compile", "-c", "-r", integrationTestsRepo.getPath(),
                    Artifacts.GUAVA, Artifacts.APACHE_COMMONS_COMPRESS, Artifacts.JUNIT5_ENGINE, Artifacts.GROOVY);
            System.out.println("STDOUT: " + result.getStdout());
            System.out.println("STDERR RESULT: " + result.getStderr());
            verifySuccessful("install int-tests repository", result);

            var groovydocDir = getGroovydocToolDir();
            System.out.println("Installing Groovydoc tool classpath for integration tests at " +
                    groovydocDir.getPath());
            result = runner.run("-r", MavenUtils.MAVEN_CENTRAL_URL,
                    "install", Artifacts.GROOVYDOC_TOOL, "-s", "runtime", "-d", groovydocDir.getPath());
            verifySuccessful("install groovydoc tool", result);
        } else {
            System.out.println("Skipping creating a new Maven repository for integration tests as repo already exists");
        }
    }

    protected String getGroovydocToolClasspath() {
        var dir = getGroovydocToolDir();
        var jars = FileUtils.collectFiles(dir.getPath(), (d, name) -> name.endsWith(".jar"));
        return String.join(File.pathSeparator, jars.files);
    }

    public ToolRunResult run(String... args) {
        class ExitError extends Error {
        }

        var exitCode = new AtomicInteger();
        var outStream = new ByteArrayOutputStream();
        var out = new PrintStream(outStream);

        try {
            new Main(args, (code) -> {
                exitCode.set(code);
                throw new ExitError();
            }, (verbose) -> new JBuildLog(out, verbose));
        } catch (ExitError e) {
            // expected
        }

        return new MemoryToolRunResult(exitCode.get(), args, outStream.toString(UTF_8), "");
    }

    public ToolRunResult runWithIntTestRepo(String... args) {
        return runWithRepo(integrationTestsRepo.toPath(), args);
    }

    public ToolRunResult runWithRepo(Path repoPath, String... args) {
        var commandArgs = new String[args.length + 2];
        commandArgs[0] = "-r";
        commandArgs[1] = repoPath.toString();
        System.arraycopy(args, 0, commandArgs, 2, args.length);
        return run(commandArgs);
    }

    public static void verifySuccessful(String tool, ToolRunResult result) {
        if (result.exitCode() != 0) {
            throw new JBuildException("unexpected error when executing " + tool +
                    ". Tool output:" + LINE_END + result.getStdout() + LINE_END + LINE_END +
                    "stderr:" + LINE_END + result.getStderr(), ACTION_ERROR);
        }
    }
}
