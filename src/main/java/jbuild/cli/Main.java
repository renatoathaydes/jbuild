package jbuild.cli;

import jbuild.artifact.Artifact;
import jbuild.commands.DepsCommandExecutor;
import jbuild.commands.FetchCommandExecutor;
import jbuild.errors.JBuildException;
import jbuild.errors.JBuildException.ErrorCause;
import jbuild.log.JBuildLog;
import jbuild.util.Executable;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
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
            "    jbuild <root-option> <cmd> <cmd-args...> \n" +
            "Options:\n" +
            "    --verbose\n" +
            "    -V        log verbose output.\n" +
            "    --version\n" +
            "    -v        print JBuild version and exit.\n" +
            "    --help\n" +
            "    -h        print this usage message.\n" +
            "\n" +
            "Available commands:\n" +
            "\n" +
            "  * fetch\n" +
            "      Usage:\n" +
            "        jbuild fetch <options... | artifact...>\n" +
            "      Options:\n" +
            "        --directory\n" +
            "        -d        output directory.\n" +
            "      Example:\n" +
            "        jbuild fetch -d libs org.apache.commons:commons-lang3:3.12.0";

    public static void main(String[] args) {
        new Main(args);
    }

    private final JBuildLog log;

    private Main(String[] args) {
        var startTime = System.currentTimeMillis();

        var options = Options.parse(args);
        this.log = new JBuildLog(System.out, options.verbose);

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

        if (options.command.isBlank()) {
            exitWithError("No command given to execute. Run jbuild --help for usage.", USER_INPUT, startTime);
        }

        switch (options.command) {
            case "fetch":
                fetchArtifacts(options, startTime);
                break;
            case "deps":
                listDeps(options, startTime);
                break;
            default:
                exitWithError("Unknown command: " + options.command +
                        ". Run jbuild --help for usage.", USER_INPUT, startTime);
        }

    }

    private void listDeps(Options options, long startTime) {
        var artifacts = parseArtifacts(startTime, options.commandArgs);

        withErrorHandling(() -> {
            DepsCommandExecutor.createDefault(log).fetchDependenciesOf(artifacts).forEach((artifact, pom) -> {
                log.println("Dependencies of " + artifact + ":\n");
                for (var dependency : pom.getDependencies()) {
                    log.println("  * " + dependency);
                }
            });
        }, startTime);
    }

    private void fetchArtifacts(Options options, long startTime) {
        var fetchOptions = FetchOptions.parse(options.commandArgs);

        if (fetchOptions.artifacts.isEmpty()) {
            log.println("No artifacts were provided. Nothing to do.");
            return;
        }

        var artifacts = parseArtifacts(startTime, fetchOptions.artifacts);

        var latch = new CountDownLatch(artifacts.size());
        var anyError = new AtomicReference<ErrorCause>();

        FetchCommandExecutor.createDefault(log).fetchArtifacts(artifacts, new File(fetchOptions.outDir))
                .forEach((artifact, successCompletion) -> successCompletion.whenComplete((ok, err) -> {
                    try {
                        if (err != null || ok.isEmpty()) {
                            anyError.set(ErrorCause.UNKNOWN);

                            // exceptional completions are not reported by the executor, so we need to report here
                            if (err != null) {
                                log.print("An error occurred while fetching " + artifact + ": ");
                                if (err instanceof JBuildException) {
                                    log.println(err.getMessage());
                                    anyError.set(((JBuildException) err).getErrorCause());
                                } else {
                                    log.println(err.toString());
                                }
                            } else { // ok is empty: non-exceptional error
                                log.println("Failed to fetch " + artifact);
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                }));

        withErrorHandling(() -> {
            latch.await();

            var errorCause = anyError.get();
            if (errorCause != null) {
                exitWithError("Could not fetch all artifacts successfully", errorCause, startTime);
            }

            log.verbosePrintln(() -> (artifacts.size() > 1 ? "All " + artifacts.size() + " artifacts" : "Artifact") +
                    " successfully downloaded to " + fetchOptions.outDir);
        }, startTime);
    }

    private void withErrorHandling(Executable exe, long startTime) {
        try {
            exe.run();
        } catch (JBuildException e) {
            exitWithError(e.getMessage(), e.getErrorCause(), startTime);
        } catch (Exception e) {
            exitWithError(e.toString(), ErrorCause.UNKNOWN, startTime);
        }
        log.println(() -> "Build passed in " + time(startTime) + "!");
    }

    private Set<? extends Artifact> parseArtifacts(long startTime, List<String> coordinates) {
        Set<Artifact> artifacts;
        try {
            artifacts = coordinates.stream()
                    .map(Artifact::parseCoordinates)
                    .collect(toSet());
        } catch (IllegalArgumentException e) {
            exitWithError(e.getMessage(), USER_INPUT, startTime);
            throw new RuntimeException("unreachable");
        }

        log.verbosePrintln(() -> "Parsed artifacts coordinates:\n" + artifacts.stream()
                .map(a -> "  * " + a + "\n")
                .collect(joining()));

        return artifacts;
    }

    private void exitWithError(String message, ErrorCause cause, long startTime) {
        log.print("ERROR: ");
        log.println(message);
        log.println(() -> "Build failed in " + time(startTime) +
                "! [code=" + cause.name().toLowerCase(Locale.ROOT) + "]");
        System.exit(exitCode(cause));
    }

    private static String time(long startTime) {
        return (System.currentTimeMillis() - startTime) + "ms";
    }

    private static int exitCode(ErrorCause errorCause) {
        return errorCause.ordinal() + 1;
    }
}
