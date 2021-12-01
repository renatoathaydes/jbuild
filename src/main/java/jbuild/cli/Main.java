package jbuild.cli;

import jbuild.DependenciesManager;
import jbuild.artifact.Artifact;
import jbuild.artifact.ResolvedArtifact;
import jbuild.errors.HttpError;
import jbuild.util.AsyncUtils;
import jbuild.util.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class Main {
    static final String JBUILD_VERSION = "1.0";

    static final String USAGE = "------ JBuild CLI ------\n" +
            "Version: " + JBUILD_VERSION + "\n" +
            "\n" +
            "Utility to build Java (JVM) applications.\n" +
            "It can only download Maven artifacts for now.\n" +
            "\n" +
            "Usage:\n" +
            "    java <options> <artifacts>...\n" +
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
        var options = new Options().parse(args);

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

        var artifacts = options.artifacts.stream()
                .map(Artifact::parseCoordinates)
                .collect(toList());

        if (options.verbose) {
            System.out.print("Parsed artifacts coordinates:\n" + artifacts.stream()
                    .map(a -> "  * " + a + "\n")
                    .collect(joining()));
        }

        fetchArtifacts(artifacts, new File(options.outDir), options.verbose);
    }

    private static void fetchArtifacts(List<Artifact> artifacts, File outDir, boolean verbose) {
        var dirExists = FileUtils.ensureDirectoryExists(outDir);
        if (!dirExists) {
            exitWithError("ERROR: Directory does not exist and cannot be created: " + outDir.getPath(), 1);
        }

        var latch = new CountDownLatch(artifacts.size());
        var anyError = new AtomicBoolean(false);

        AsyncUtils.waitForEach(new DependenciesManager().downloadAllByHttp(artifacts))
                .forEach(resolution -> resolution.use(
                        resolved -> writeArtifact(resolved, outDir, anyError, verbose),
                        error -> {
                            anyError.set(true);
                            handleError(error, resolution.requestedArtifact);
                        },
                        latch::countDown));

        try {
            var ok = latch.await(2, TimeUnit.MINUTES);
            if (!ok) {
                exitWithError("ERROR: Timeout while waiting for artifacts!", 4);
            }
        } catch (InterruptedException e) {
            exitWithError("ERROR: Interrupted while waiting for artifact downloads!", 3);
        }

        if (anyError.get()) {
            exitWithError("Build failed!", 1);
        }

        if (verbose) {
            System.out.println("All artifacts successfully downloaded to " + outDir.getPath());
        }
    }

    private static void exitWithError(String s, int i) {
        System.err.println(s);
        System.exit(i);
    }

    private static void handleError(HttpError<byte[]> error, Artifact artifact) {
        System.out.println("ERROR: Could not fetch " + artifact +
                " http status = " + error.httpResponse.statusCode() + ", http body = " +
                new String(error.httpResponse.body(), StandardCharsets.UTF_8));
    }

    private static void writeArtifact(ResolvedArtifact resolvedArtifact,
                                      File outDir,
                                      AtomicBoolean anyError,
                                      boolean verbose) {
        var file = new File(outDir, resolvedArtifact.artifact.toFileName());
        try (var out = new FileOutputStream(file)) {
            try {
                out.write(resolvedArtifact.contents);
                if (verbose) {
                    System.out.println("Wrote artifact " + resolvedArtifact.artifact + " to " + file.getPath());
                }
            } catch (IOException e) {
                System.err.println("ERROR: unable to write to file " + file + " due to " + e);
                anyError.set(true);
            }
        } catch (IOException e) {
            System.err.println("ERROR: unable to open file " + file + " due to " + e);
            anyError.set(true);
        }
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
