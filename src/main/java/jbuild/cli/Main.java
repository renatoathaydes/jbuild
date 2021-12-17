package jbuild.cli;

import jbuild.artifact.Artifact;
import jbuild.artifact.file.ArtifactFileWriter;
import jbuild.commands.DepsCommandExecutor;
import jbuild.commands.FetchCommandExecutor;
import jbuild.commands.InstallCommandExecutor;
import jbuild.commands.VersionsCommandExecutor;
import jbuild.errors.JBuildException;
import jbuild.errors.JBuildException.ErrorCause;
import jbuild.log.JBuildLog;
import jbuild.maven.MavenMetadata;
import jbuild.util.Executable;
import jbuild.util.FileUtils;
import jbuild.util.NonEmptyCollection;

import java.io.File;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static jbuild.artifact.file.ArtifactFileWriter.WriteMode.FLAT_DIR;
import static jbuild.artifact.file.ArtifactFileWriter.WriteMode.MAVEN_REPOSITORY;
import static jbuild.errors.JBuildException.ErrorCause.IO_WRITE;
import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;
import static jbuild.util.TextUtils.durationText;

public final class Main {

    static final String JBUILD_VERSION = "0.0";

    static final String USAGE = "------ JBuild CLI ------\n" +
            "Version: " + JBUILD_VERSION + "\n" +
            "\n" +
            "Utility to build Java (JVM) applications.\n" +
            "This is work in progress!\n" +
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
            "    Fetches Maven artifacts from the local Maven repo or Maven Central.\n" +
            "      Usage:\n" +
            "        jbuild fetch <options... | artifact...>\n" +
            "      Options:\n" +
            "        --directory\n" +
            "        -d        output directory.\n" +
            "      Example:\n" +
            "        jbuild fetch -d libs org.apache.commons:commons-lang3:3.12.0\n" +
            "\n" +
            "  * install\n" +
            "    Install Maven artifacts from the local Maven repo or Maven Central.\n" +
            "    Unlike fetch, install downloads artifacts and their dependencies, and can write\n" +
            "    them into a flat directory or in the format of a Maven repository.\n" +
            "      Usage:\n" +
            "        jbuild install <options... | artifact...>\n" +
            "      Options:\n" +
            "        --directory\n" +
            "        -d        (flat) output directory.\n" +
            "        --repository\n" +
            "        -r        (Maven repository root) output directory.\n" +
            "        --optional\n" +
            "        -O        include optional dependencies.\n" +
            "        --scope\n" +
            "        -s        scope to include (can be passed more than once).\n" +
            "                  The runtime scope is used by default.\n" +
            "      Note:\n" +
            "        The --directory and --repository options are mutually exclusive.\n" +
            "        By default, the equivalent of '-d out/' is used." +
            "      Example:\n" +
            "        jbuild install -s compile org.apache.commons:commons-lang3:3.12.0\n" +
            "\n" +
            "  * deps\n" +
            "    List the dependencies of the given artifacts.\n" +
            "      Usage:\n" +
            "        jbuild deps <options... | artifact...>\n" +
            "      Options:\n" +
            "        --optional\n" +
            "        -O        include optional dependencies.\n" +
            "        --scope\n" +
            "        -s        scope to include (can be passed more than once).\n" +
            "                  All scopes are listed by default.\n" +
            "        --transitive\n" +
            "        -t        include transitive dependencies.\n" +
            "      Example:\n" +
            "        jbuild deps com.google.guava:guava:31.0.1-jre junit:junit:4.13.2\n" +
            "\n" +
            "  * versions\n" +
            "    List the versions of the given artifacts that are available on Maven Central.\n" +
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
            case "install":
                installArtifacts(options, startTime);
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
        var depsOptions = DepsOptions.parse(options.commandArgs);
        var artifacts = parseArtifacts(startTime, depsOptions.artifacts);

        if (artifacts.isEmpty()) {
            log.println("No artifacts were provided. Nothing to do.");
            return;
        }

        var latch = new CountDownLatch(artifacts.size());
        var anyError = new AtomicReference<ErrorCause>();
        var treeLogger = new DependencyTreeLogger(log, depsOptions);

        DepsCommandExecutor.createDefault(log).fetchDependencyTree(
                        artifacts, depsOptions.scopes, depsOptions.transitive, depsOptions.optional)
                .forEach((artifact, successCompletion) -> successCompletion.whenComplete((ok, err) -> {
                    try {
                        reportErrors(anyError, artifact, ok, err);
                        if (anyError.get() == null && ok.isPresent()) {
                            treeLogger.log(ok.get());
                        }
                    } finally {
                        latch.countDown();
                    }
                }));

        withErrorHandling(() -> {
            latch.await();

            var errorCause = anyError.get();
            if (errorCause != null) {
                exitWithError("Could not fetch all Maven dependencies successfully", errorCause, startTime);
            }
        }, startTime);
    }

    private void installArtifacts(Options options, long startTime) {
        var installOptions = InstallOptions.parse(options.commandArgs);
        var artifacts = parseArtifacts(startTime, installOptions.artifacts);

        if (artifacts.isEmpty()) {
            log.println("No artifacts were provided. Nothing to do.");
            return;
        }

        var fileWriter = installOptions.outDir == null
                ? new ArtifactFileWriter(new File(installOptions.repoDir), MAVEN_REPOSITORY)
                : new ArtifactFileWriter(new File(installOptions.outDir), FLAT_DIR);

        var latch = new CountDownLatch(artifacts.size());
        var anyError = new AtomicReference<ErrorCause>();

        InstallCommandExecutor.create(log, fileWriter).installDependencyTree(
                artifacts, installOptions.scopes, true, installOptions.optional
        ).whenComplete((successCount, err) -> {
            try {
                if (err == null) {
                    var successes = successCount.map(ok -> ok, errors -> {
                        reportErrors(anyError, errors);
                        return -1L;
                    });
                    if (successes > 0) {
                        log.println(() -> "Successfully installed " + successes +
                                " artifact" + (successes == 1 ? "" : "s") + " at " + fileWriter.directory);
                    }
                } else {
                    log.print(err);
                }
            } finally {
                latch.countDown();
            }
        });

        withErrorHandling(() -> {
            latch.await();

            var errorCause = anyError.get();
            if (errorCause != null) {
                exitWithError("Could not install all artifacts successfully", errorCause, startTime);
            }
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

        var fileWriter = new ArtifactFileWriter(outDir, FLAT_DIR);
        var latch = new CountDownLatch(artifacts.size());
        var anyError = new AtomicReference<ErrorCause>();

        FetchCommandExecutor.createDefault(log).fetchArtifacts(artifacts, fileWriter)
                .forEach((artifact, completion) -> completion.whenComplete((ok, err) -> {
                    try {
                        reportErrors(anyError, artifact, ok, err);
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
        var versionsOptions = VersionsOptions.parse(options.commandArgs);
        var artifacts = parseArtifacts(startTime, versionsOptions.artifacts);

        if (artifacts.isEmpty()) {
            log.println("No artifacts were provided. Nothing to do.");
            return;
        }

        var latch = new CountDownLatch(artifacts.size());
        var anyError = new AtomicReference<ErrorCause>();
        var metadataByArtifact = new ConcurrentSkipListMap<Artifact, MavenMetadata>(comparing(Artifact::getCoordinates));

        new VersionsCommandExecutor(log).getVersions(artifacts).forEach((artifact, eitherCompletionStage) ->
                eitherCompletionStage.whenComplete((completion, err) -> {
                    try {
                        if (err == null) {
                            completion.use(
                                    ok -> metadataByArtifact.put(artifact, ok),
                                    er -> reportErrors(anyError, artifact, Optional.empty(), er));
                        } else {
                            reportErrors(anyError, artifact, Optional.empty(), err);
                        }
                    } finally {
                        latch.countDown();
                    }
                }));

        withErrorHandling(() -> {
            latch.await();

            var versionLogger = new VersionLogger(log);
            metadataByArtifact.forEach(versionLogger::log);

            var errorCause = anyError.get();
            if (errorCause != null) {
                exitWithError("Could not fetch all versions successfully", errorCause, startTime);
            }
        }, startTime);
    }

    private void reportErrors(AtomicReference<ErrorCause> anyError,
                              Artifact artifact,
                              Optional<?> result,
                              Throwable err) {
        if (err != null || result.isEmpty()) {
            anyError.set(ErrorCause.UNKNOWN);

            // exceptional completions are not reported by the executor, so we need to report here
            if (err != null) {
                log.print(() -> "An error occurred while processing " + artifact.getCoordinates() + ": ");
                if (err instanceof JBuildException) {
                    log.println(err.getMessage());
                    anyError.set(((JBuildException) err).getErrorCause());
                } else {
                    log.println(err.toString());
                }
            } else { // ok is empty: non-exceptional error
                log.println(() -> "Failed to handle " + artifact.getCoordinates());
            }
        }
    }

    private void reportErrors(AtomicReference<ErrorCause> anyError, NonEmptyCollection<Throwable> errors) {
        var errRefSet = false;
        for (var error : errors) {
            if (error instanceof JBuildException) {
                anyError.set(((JBuildException) error).getErrorCause());
                errRefSet = true;
            }
            log.print(error);
        }
        if (!errRefSet && anyError.get() == null) {
            anyError.set(ErrorCause.UNKNOWN);
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
        log.println(() -> "JBuild success in " + time(startTime) + "!");
    }

    private Set<? extends Artifact> parseArtifacts(long startTime, Set<String> coordinates) {
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
        log.println(() -> "JBuild failed in " + time(startTime) +
                "! [error-type=" + cause.name().toLowerCase(Locale.ROOT) + "]");
        System.exit(exitCode(cause));
    }

    private static CharSequence time(long startTime) {
        return durationText(Duration.ofMillis(System.currentTimeMillis() - startTime));
    }

    private static int exitCode(ErrorCause errorCause) {
        return errorCause.ordinal() + 1;
    }
}
