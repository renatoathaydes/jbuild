package jbuild.cli;

import jbuild.artifact.Artifact;
import jbuild.artifact.file.ArtifactFileWriter;
import jbuild.commands.DepsCommandExecutor;
import jbuild.commands.FetchCommandExecutor;
import jbuild.commands.VersionsCommandExecutor;
import jbuild.errors.JBuildException;
import jbuild.errors.JBuildException.ErrorCause;
import jbuild.log.JBuildLog;
import jbuild.maven.MavenMetadata;
import jbuild.maven.MavenPom;
import jbuild.maven.Scope;
import jbuild.util.Executable;
import jbuild.util.FileUtils;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static jbuild.errors.JBuildException.ErrorCause.IO_WRITE;
import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;
import static jbuild.util.CollectionUtils.sorted;

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
            "    Fetches Maven artifacts from the local Maven repo or Maven Central." +
            "      Usage:\n" +
            "        jbuild fetch <options... | artifact...>\n" +
            "      Options:\n" +
            "        --directory\n" +
            "        -d        output directory.\n" +
            "      Example:\n" +
            "        jbuild fetch -d libs org.apache.commons:commons-lang3:3.12.0" +
            "\n" +
            "  * deps\n" +
            "    List the direct dependencies of the given artifacts." +
            "      Usage:\n" +
            "        jbuild deps <artifact...>\n" +
            "      Example:\n" +
            "        jbuild deps com.google.guava:guava:31.0.1-jre junit:junit:4.13.2\n" +
            "\n" +
            "  * versions\n" +
            "    List the versions of the given artifacts that are available on Maven Central." +
            "      Usage:\n" +
            "        jbuild versions <artifact...>\n" +
            "      Example:\n" +
            "        jbuild versions junit:junit\n" +
            "";

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
            case "versions":
                listVersions(options, startTime);
                break;
            default:
                exitWithError("Unknown command: " + options.command +
                        ". Run jbuild --help for usage.", USER_INPUT, startTime);
        }

    }

    private void listDeps(Options options, long startTime) {
        var artifacts = parseArtifacts(startTime, options.commandArgs);

        if (artifacts.isEmpty()) {
            log.println("No artifacts were provided. Nothing to do.");
            return;
        }

        var latch = new CountDownLatch(artifacts.size());
        var anyError = new AtomicReference<ErrorCause>();
        var pomByArtifact = new ConcurrentSkipListMap<Artifact, MavenPom>(comparing(Artifact::getCoordinates));

        var depsCommandExecutor = DepsCommandExecutor.createDefault(log);

        depsCommandExecutor.fetchPoms(artifacts).forEach((artifact, pomCompletion) -> {
            pomCompletion.whenComplete((ok, err) -> {
                try {
                    if (ok.isPresent()) {
                        pomByArtifact.put(artifact, ok.get());
                    } else {
                        reportErrors(anyError, artifact, false, err);
                    }
                } finally {
                    latch.countDown();
                }
            });
        });

        withErrorHandling(() -> {
            latch.await();

            var errorCause = anyError.get();
            if (errorCause != null) {
                exitWithError("Could not fetch all Maven POMs successfully", errorCause, startTime);
            }

            pomByArtifact.forEach((artifact, pom) -> {
                log.println("Dependencies of " + artifact.getCoordinates() + ":");
                if (pom.getDependencies().isEmpty()) {
                    log.println("  * no dependencies");
                    return;
                }

                var groupedDeps = pom.getDependencies().stream()
                        .collect(groupingBy(dep -> dep.scope));

                // iterated sorted by scope declaration order
                for (Scope scope : Scope.values()) {
                    var deps = groupedDeps.get(scope);
                    if (deps != null && !deps.isEmpty()) {
                        log.println("  - scope " + scope);
                        for (var dependency : sorted(deps, comparing(dep -> dep.artifact.getCoordinates()))) {
                            log.println("    * " + dependency.artifact.getCoordinates());
                        }
                    }
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

        var outDir = new File(fetchOptions.outDir);

        var dirExists = FileUtils.ensureDirectoryExists(outDir);
        if (!dirExists) {
            throw new JBuildException(
                    "Output directory does not exist and cannot be created: " + outDir.getPath(),
                    IO_WRITE);
        }

        var fileWriter = new ArtifactFileWriter(outDir);
        var latch = new CountDownLatch(artifacts.size());
        var anyError = new AtomicReference<ErrorCause>();

        FetchCommandExecutor.createDefault(log).fetchArtifacts(artifacts, fileWriter)
                .forEach((artifact, successCompletion) -> successCompletion.whenComplete((ok, err) -> {
                    try {
                        reportErrors(anyError, artifact, ok.isEmpty(), err);
                    } finally {
                        latch.countDown();
                    }
                }));

        withErrorHandling(() -> {
            try {
                latch.await();
            } finally {
                fileWriter.close();
            }

            var errorCause = anyError.get();
            if (errorCause != null) {
                exitWithError("Could not fetch all artifacts successfully", errorCause, startTime);
            }

            log.verbosePrintln(() -> (artifacts.size() > 1 ? "All " + artifacts.size() + " artifacts" : "Artifact") +
                    " successfully downloaded to " + fetchOptions.outDir);
        }, startTime);
    }

    private void listVersions(Options options, long startTime) {
        var artifacts = parseArtifacts(startTime, options.commandArgs);

        if (artifacts.isEmpty()) {
            log.println("No artifacts were provided. Nothing to do.");
            return;
        }

        var latch = new CountDownLatch(artifacts.size());
        var anyError = new AtomicReference<ErrorCause>();
        var metadataByArtifact = new ConcurrentSkipListMap<Artifact, MavenMetadata>(comparing(Artifact::getCoordinates));

        new VersionsCommandExecutor(log).getVersions(artifacts).forEach((artifact, eitherCompletionStage) ->
                eitherCompletionStage.whenComplete((completion, throwable) -> {
                    if (throwable != null) {
                        reportErrors(anyError, artifact, false, throwable);
                        latch.countDown();
                        return;
                    }
                    try {
                        completion.use(
                                ok -> metadataByArtifact.put(artifact, ok),
                                err -> reportErrors(anyError, artifact, false, err));
                    } finally {
                        latch.countDown();
                    }
                }));

        withErrorHandling(() -> {
            latch.await();
            metadataByArtifact.forEach((artifact, mavenMetadata) -> {
                log.println("Versions of " + artifact.getCoordinates() + ":");

                var latest = mavenMetadata.getLatestVersion();
                var release = mavenMetadata.getReleaseVersion();
                var versions = mavenMetadata.getVersions();

                if (!latest.isBlank()) {
                    log.println("  * Latest: " + latest);
                }
                if (!release.isBlank()) {
                    log.println("  * Release: " + latest);
                }

                if (versions.isEmpty()) {
                    log.println("  * no versions available");
                } else {
                    log.println("  * All versions:");
                    for (var version : versions) {
                        log.println("    - " + version);
                    }
                }
            });

            var errorCause = anyError.get();
            if (errorCause != null) {
                exitWithError("Could not fetch all versions successfully", errorCause, startTime);
            }
        }, startTime);
    }

    private void reportErrors(AtomicReference<ErrorCause> anyError,
                              Artifact artifact,
                              boolean isUnknownError,
                              Throwable err) {
        if (err != null || isUnknownError) {
            anyError.set(ErrorCause.UNKNOWN);

            // exceptional completions are not reported by the executor, so we need to report here
            if (err != null) {
                log.print("An error occurred while processing " + artifact + ": ");
                if (err instanceof JBuildException) {
                    log.println(err.getMessage());
                    anyError.set(((JBuildException) err).getErrorCause());
                } else {
                    log.println(err.toString());
                }
            } else { // ok is empty: non-exceptional error
                log.println("Failed to handle " + artifact);
            }
        }
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
