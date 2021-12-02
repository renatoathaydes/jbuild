package jbuild.cli;

import jbuild.artifact.Artifact;
import jbuild.artifact.ResolvedArtifact;
import jbuild.errors.JBuildException;

import java.io.File;
import java.util.ArrayList;
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
        var startTime = System.currentTimeMillis();

        var options = new Options().parse(args);

        if (options.verbose) {
            System.out.println("Parsed CLI options in " + time(startTime));
        }

        if (options.help) {
            System.out.println(USAGE);
            return;
        }

        if (options.version) {
            System.out.println(JBUILD_VERSION);
            return;
        }

        if (options.artifacts.isEmpty()) {
            System.out.println("No artifacts were provided. Nothing to do.");
            return;
        }

        List<Artifact> artifacts;
        try {
            artifacts = options.artifacts.stream()
                    .map(Artifact::parseCoordinates)
                    .collect(toList());
        } catch (IllegalArgumentException e) {
            exitWithError("ERROR: " + e.getMessage(), exitCode(USER_INPUT), startTime);
            return;
        }

        if (options.verbose) {
            System.out.print("Parsed artifacts coordinates:\n" + artifacts.stream()
                    .map(a -> "  * " + a + "\n")
                    .collect(joining()));
        }

        try {
            var resolvedArtifacts = CommandExecutor.fetchArtifacts(
                    artifacts, new File(options.outDir), options.verbose);
            reportArtifacts(resolvedArtifacts, options.verbose);
            System.out.println("Build passed in " + time(startTime) + "!");
        } catch (JBuildException e) {
            exitWithError(e.getMessage(), exitCode(e.getErrorCause()), startTime);
        } catch (Exception e) {
            exitWithError(e.getMessage(), exitCode(JBuildException.ErrorCause.UNKNOWN), startTime);
        }
    }

    private static void reportArtifacts(Collection<ResolvedArtifact> resolvedArtifacts, boolean verbose) {
        System.out.println("Resolved " + resolvedArtifacts.size() + " artifacts.");
        if (verbose) {
            for (var artifact : resolvedArtifacts) {
                System.out.println("  * " + artifact.artifact + " (" + artifact.contents.length + " bytes)");
            }
        }
    }

    private static void exitWithError(String message, int exitCode, long startTime) {
        System.err.println(message);
        System.err.println("Build failed in " + time(startTime) +
                "! [exit code=" + exitCode + "");
        System.exit(exitCode);
    }

    private static String time(long startTime) {
        return (System.currentTimeMillis() - startTime) + "ms";
    }

    private static int exitCode(JBuildException.ErrorCause errorCause) {
        return errorCause.ordinal();
    }
}

final class Options {

    final List<String> artifacts = new ArrayList<>();
    String outDir = "out";
    boolean verbose;
    boolean help;
    boolean version;

    Options parse(String[] args) {
        var nextIsDir = false;
        for (String arg : args) {
            if (nextIsDir) {
                outDir = arg;
                nextIsDir = false;
            } else if (!arg.startsWith("-")) {
                artifacts.add(arg);
            } else if (isEither(arg, "-V", "--verbose")) {
                verbose = true;
            } else if (isEither(arg, "-v", "--version")) {
                version = true;
                return this;
            } else if (isEither(arg, "-h", "--help")) {
                help = true;
                return this;
            } else if (isEither(arg, "-d", "--directory")) {
                nextIsDir = true;
            } else {
                System.err.println("Invalid option: " + arg + "\nRun jbuild --help for usage.");
            }
        }
        return this;
    }

    private static boolean isEither(String arg, String opt1, String opt2) {
        return opt1.equals(arg) || opt2.equals(arg);
    }
}
