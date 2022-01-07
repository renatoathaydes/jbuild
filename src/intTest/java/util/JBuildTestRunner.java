package util;

import jbuild.cli.Main;
import jbuild.errors.JBuildException;
import jbuild.java.Tools;
import jbuild.log.JBuildLog;
import org.junit.jupiter.api.BeforeAll;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;
import static util.JBuildTestRunner.SystemProperties.integrationTestsRepo;

public class JBuildTestRunner {

    public interface Artifacts {
        String GUAVA = "com.google.guava:guava:31.0.1-jre";
        String APACHE_COMMONS_COMPRESS = "org.apache.commons:commons-compress:1.21";
        String JUNIT5_ENGINE = "org.junit.jupiter:junit-jupiter-engine:5.7.0";
    }

    public interface SystemProperties {
        File integrationTestsRepo = new File(System.getProperty("tests.int-tests.repo"));
    }

    @BeforeAll
    static void initialize() {
        if (!integrationTestsRepo.isDirectory()) {
            System.out.println("Installing Maven repository for integration tests at " + integrationTestsRepo.getPath());
            var result = new JBuildTestRunner().run("install",
                    "-O", "-s", "compile", "-r", integrationTestsRepo.getPath(),
                    Artifacts.GUAVA, Artifacts.APACHE_COMMONS_COMPRESS, Artifacts.JUNIT5_ENGINE);
            verifySuccessful("install", result);
        } else {
            System.out.println("Skipping creating a new Maven repository for integration tests as repo already exists");
        }
    }

    public Tools.ToolRunResult run(String... args) {
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

        return new Tools.ToolRunResult(exitCode.get(), args, outStream.toString(UTF_8), "");
    }

    public Tools.ToolRunResult runWithIntTestRepo(String... args) {
        var commandArgs = new String[args.length + 2];
        commandArgs[0] = "-r";
        commandArgs[1] = integrationTestsRepo.getPath();
        System.arraycopy(args, 0, commandArgs, 2, args.length);
        return run(commandArgs);
    }

    public static void verifySuccessful(String tool, Tools.ToolRunResult result) {
        if (result.exitCode != 0) {
            throw new JBuildException("unexpected error when executing " + tool +
                    ". Tool output:\n" + result.stdout + "\n\nstderr:\n" + result.stderr, ACTION_ERROR);
        }
    }
}
