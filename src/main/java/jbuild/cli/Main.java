package jbuild.cli;

import jbuild.artifact.Artifact;
import jbuild.artifact.ResolvedArtifact;
import jbuild.errors.JBuildException;
import jbuild.log.JBuildLog;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;

public class Main {

    static final String JBUILD_VERSION = "0.0";

    static final String USAGE = "------ JBuild CLI ------\n" +
            "Version: " + JBUILD_VERSION + "\n" +
            "\n" +
            "Utility to build Java (JVM) applications.\n" +
            "It can only download Maven artifacts for now.\n" +
            "\n" +
            "Usage:\n" +
            "    jbuild <options> <artifacts>...\n" +
            "Options:\n" +
            "    --directory\n" +
            "    -d        output directory.\n" +
            "    --verbose\n" +
            "    -V        log verbose output.\n" +
            "    --version\n" +
            "    -v        print JBuild version and exit.\n" +
            "    --help\n" +
            "    -h        print this usage message.\n" +
            "\n" +
            "Example:\n" +
            "  jbuild -d out org.apache.maven:maven:3.3.9";

    public static void main(String[] args) {
        new Main(args);
    }

    private final JBuildLog log;
    private final CommandExecutor commandExecutor;

    private Main(String[] args) {
        var startTime = System.currentTimeMillis();

        var options = new Options().parse(args);

        this.log = new JBuildLog(System.out, options.verbose);
        this.commandExecutor = new CommandExecutor(log);

        log.verbosePrintln(() -> "Parsed CLI options in " + time(startTime));

        run(options, startTime);
    }

    private void run(Options options, long startTime) {
        if (options.help) {
            log.println(USAGE);
            return;
        }

        if (options.version) {
            log.println(JBUILD_VERSION);
            return;
        }

        fetchArtifacts(options, startTime);
    }

    private void fetchArtifacts(Options options, long startTime) {
        if (options.artifacts.isEmpty()) {
            log.println("No artifacts were provided. Nothing to do.");
            return;
        }

        List<Artifact> artifacts;
        try {
            artifacts = options.artifacts.stream()
                    .map(Artifact::parseCoordinates)
                    .collect(toList());
        } catch (IllegalArgumentException e) {
            exitWithError(e.getMessage(), exitCode(USER_INPUT), startTime);
            return;
        }

        log.verbosePrintln(() -> "Parsed artifacts coordinates:\n" + artifacts.stream()
                .map(a -> "  * " + a + "\n")
                .collect(joining()));

        try {
            var resolvedArtifacts = commandExecutor.fetchArtifacts(
                    artifacts, new File(options.outDir));
            reportArtifacts(resolvedArtifacts);
            log.println(() -> "Build passed in " + time(startTime) + "!");
        } catch (JBuildException e) {
            exitWithError(e.getMessage(), exitCode(e.getErrorCause()), startTime);
        } catch (Exception e) {
            exitWithError(e.toString(), exitCode(JBuildException.ErrorCause.UNKNOWN), startTime);
        }
    }

    private void reportArtifacts(Collection<ResolvedArtifact> resolvedArtifacts) {
        log.println("Resolved " + resolvedArtifacts.size() + " artifacts.");
        if (log.isVerbose()) {
            for (var artifact : resolvedArtifacts) {
                log.println("  * " + artifact.artifact + " (" + artifact.contentLength + " bytes)");
            }
        }
    }

    private void exitWithError(String message, int exitCode, long startTime) {
        log.print("ERROR: ");
        log.println(message);
        log.println(() -> "Build failed in " + time(startTime) + "! [exit code=" + exitCode + "]");
        System.exit(exitCode);
    }

    private static String time(long startTime) {
        return (System.currentTimeMillis() - startTime) + "ms";
    }

    private static int exitCode(JBuildException.ErrorCause errorCause) {
        return errorCause.ordinal() + 1;
    }
}
