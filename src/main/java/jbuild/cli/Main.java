package jbuild.cli;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactRetriever;
import jbuild.artifact.file.ArtifactFileWriter;
import jbuild.artifact.http.DefaultHttpClient;
import jbuild.commands.CompileCommandExecutor;
import jbuild.commands.DepsCommandExecutor;
import jbuild.commands.DoctorCommandExecutor;
import jbuild.commands.FetchCommandExecutor;
import jbuild.commands.InstallCommandExecutor;
import jbuild.commands.VersionsCommandExecutor;
import jbuild.errors.ArtifactRetrievalError;
import jbuild.errors.JBuildException;
import jbuild.errors.JBuildException.ErrorCause;
import jbuild.log.JBuildLog;
import jbuild.maven.MavenMetadata;
import jbuild.util.Executable;
import jbuild.util.FileUtils;
import jbuild.util.NonEmptyCollection;

import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static jbuild.artifact.file.ArtifactFileWriter.WriteMode.FLAT_DIR;
import static jbuild.artifact.file.ArtifactFileWriter.WriteMode.MAVEN_REPOSITORY;
import static jbuild.errors.JBuildException.ErrorCause.IO_WRITE;
import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;
import static jbuild.java.tools.Tools.verifyToolSuccessful;
import static jbuild.util.TextUtils.LINE_END;
import static jbuild.util.TextUtils.durationText;

public final class Main {

    static final String JBUILD_VERSION = "0.0";

    static final String USAGE = "------ JBuild CLI ------" + LINE_END +
            "Version: " + JBUILD_VERSION + "" + LINE_END +
            "" + LINE_END +
            "Utility to build Java (JVM) applications." + LINE_END +
            "This is work in progress!" + LINE_END +
            "" + LINE_END +
            "Usage:" + LINE_END +
            "    jbuild <root-option> <cmd> <cmd-args...> " + LINE_END +
            "Root Options:" + LINE_END +
            "    --repository" + LINE_END +
            "     -r       Maven repository to use to locate artifacts (file location or HTTP URL)." + LINE_END +
            "    --verbose" + LINE_END +
            "    -V        log verbose output." + LINE_END +
            "    --version" + LINE_END +
            "    -v        print JBuild version and exit." + LINE_END +
            "    --help" + LINE_END +
            "    -h        print this usage message." + LINE_END +
            "" + LINE_END +
            "Available commands:" + LINE_END +
            "" + LINE_END +
            "  * fetch" + LINE_END +
            "    Fetches Maven artifacts from the local Maven repo or Maven Central." + LINE_END +
            "      Usage:" + LINE_END +
            "        jbuild fetch <options... | artifact...>" + LINE_END +
            "      Options:" + LINE_END +
            "        --directory" + LINE_END +
            "        -d        output directory." + LINE_END +
            "      Example:" + LINE_END +
            "        jbuild fetch -d libs org.apache.commons:commons-lang3:3.12.0" + LINE_END +
            "" + LINE_END +
            "  * compile" + LINE_END +
            "    Compile all Java source files found in the working directory or the given input directories." + LINE_END +
            "      Usage:" + LINE_END +
            "        jbuild compile <options... | input-directory...>" + LINE_END +
            "      Options:" + LINE_END +
            "        --classpath" + LINE_END +
            "        -cp       Java classpath (may be given more than once)." + LINE_END +
            "        --directory" + LINE_END +
            "        -d        output directory." + LINE_END +
            "        --jar" + LINE_END +
            "        -j        destination jar." + LINE_END +
            "      Note:" + LINE_END +
            "        The --directory and --jar options are mutually exclusive." + LINE_END +
            "        By default, the equivalent of '-j lib.jar' is used." +
            "      Example:" + LINE_END +
            "        jbuild compile -cp libs/jsr305-3.0.2.jar" + LINE_END +
            "" + LINE_END +
            "  * install" + LINE_END +
            "    Install Maven artifacts from the local Maven repo or Maven Central." + LINE_END +
            "    Unlike fetch, install downloads artifacts and their dependencies, and can write" + LINE_END +
            "    them into a flat directory or in the format of a Maven repository." + LINE_END +
            "      Usage:" + LINE_END +
            "        jbuild install <options... | artifact...>" + LINE_END +
            "      Options:" + LINE_END +
            "        --directory" + LINE_END +
            "        -d        (flat) output directory." + LINE_END +
            "        --repository" + LINE_END +
            "        -r        (Maven repository root) output directory." + LINE_END +
            "        --optional" + LINE_END +
            "        -O        include optional dependencies." + LINE_END +
            "        --scope" + LINE_END +
            "        -s        scope to include (can be passed more than once)." + LINE_END +
            "                  The runtime scope is used by default." + LINE_END +
            "      Note:" + LINE_END +
            "        The --directory and --repository options are mutually exclusive." + LINE_END +
            "        By default, the equivalent of '-d out/' is used." +
            "      Example:" + LINE_END +
            "        jbuild install -s compile org.apache.commons:commons-lang3:3.12.0" + LINE_END +
            "" + LINE_END +
            "  * deps" + LINE_END +
            "    List the dependencies of the given artifacts." + LINE_END +
            "      Usage:" + LINE_END +
            "        jbuild deps <options... | artifact...>" + LINE_END +
            "      Options:" + LINE_END +
            "        --licenses" + LINE_END +
            "        -l        show licenses of all artifacts (requires --transitive option)." + LINE_END +
            "        --optional" + LINE_END +
            "        -O        include optional dependencies." + LINE_END +
            "        --scope" + LINE_END +
            "        -s        scope to include (can be passed more than once)." + LINE_END +
            "                  All scopes are listed by default." + LINE_END +
            "        --transitive" + LINE_END +
            "        -t        include transitive dependencies." + LINE_END +
            "      Example:" + LINE_END +
            "        jbuild deps com.google.guava:guava:31.0.1-jre junit:junit:4.13.2" + LINE_END +
            "" + LINE_END +
            "  * doctor" + LINE_END +
            "    Examines a directory trying to find a consistent set of jars (classpath) for the entrypoint(s) jar(s)." + LINE_END +
            "    This command requires user interaction by default." + LINE_END +
            "      Usage:" + LINE_END +
            "        jbuild doctor <options...> <dir>" + LINE_END +
            "      Options:" + LINE_END +
            "        --entrypoint" + LINE_END +
            "        -e        entry-point jar within the directory, or the application jar" + LINE_END +
            "                  (can be passed more than once)." + LINE_END +
            "        --yes" + LINE_END +
            "        -y        answer any question with 'yes'." + LINE_END +
            "      Example:" + LINE_END +
            "        jbuild doctor my-dir -e app.jar" + LINE_END +
            "" + LINE_END +
            "  * versions" + LINE_END +
            "    List the versions of the given artifacts that are available on Maven Central." + LINE_END +
            "      Usage:" + LINE_END +
            "        jbuild versions <artifact...>" + LINE_END +
            "      Example:" + LINE_END +
            "        jbuild versions junit:junit" + LINE_END +
            "";

    public static void main(String[] args) {
        new Main(args, System::exit, (verbose) -> new JBuildLog(System.out, verbose));
    }

    private final JBuildLog log;
    private final Consumer<Integer> exit;

    public Main(String[] args,
                Consumer<Integer> exit,
                Function<Boolean, JBuildLog> logCreator) {
        this.exit = exit;

        var startTime = System.currentTimeMillis();

        var options = Options.parse(args);
        this.log = logCreator.apply(options.verbose);

        log.verbosePrintln(() -> "Parsed CLI options in " + time(startTime));

        withErrorHandling(() -> run(options), startTime);
    }

    private void run(Options options) throws Exception {
        if (options.help) {
            log.println(USAGE);
            return;
        }

        if (options.version) {
            log.println(JBUILD_VERSION);
            return;
        }

        if (options.command.isBlank()) {
            throw new JBuildException("No command given to execute. Run jbuild --help for usage.", USER_INPUT);
        }

        switch (options.command) {
            case "compile":
                compile(options);
                break;
            case "fetch":
                fetchArtifacts(options);
                break;
            case "deps":
                listDeps(options);
                break;
            case "install":
                installArtifacts(options);
                break;
            case "doctor":
                doctor(options);
                break;
            case "versions":
                listVersions(options);
                break;
            default:
                throw new JBuildException("Unknown command: " + options.command +
                        ". Run jbuild --help for usage.", USER_INPUT);
        }
    }

    private void compile(Options options) {
        var compileOptions = CompileOptions.parse(options.commandArgs);

        var commandExecutor = new CompileCommandExecutor(log);

        var result = commandExecutor.compile(
                compileOptions.inputDirectories, compileOptions.outputDirOrJar, compileOptions.classpath
        );
        // FIXME may not be javac
        verifyToolSuccessful("javac", result);
    }

    private void doctor(Options options) throws ExecutionException, InterruptedException {
        var fixOptions = DoctorOptions.parse(options.commandArgs);

        if (fixOptions.entryPoints.isEmpty()) {
            log.println("No entry points provided, nothing to do. Please provide the entry points (jars) " +
                    "to check the classpath with the option -e, see usage for details.");
            return;
        }

        var commandExecutor = new DoctorCommandExecutor(log);

        commandExecutor.run(
                fixOptions.inputDir, fixOptions.interactive, fixOptions.entryPoints, fixOptions.typeExclusions
        ).toCompletableFuture().get();
    }

    private void listDeps(Options options) throws Exception {
        var depsOptions = DepsOptions.parse(options.commandArgs);
        var artifacts = parseArtifacts(depsOptions.artifacts);

        if (artifacts.isEmpty()) {
            log.println("No artifacts were provided. Nothing to do.");
            return;
        }

        if (depsOptions.licenses && !depsOptions.transitive) {
            // we cannot fetch dependencies' POMs to see their licenses unless we fetch transitive dependencies
            log.println(() -> "WARNING: to display dependencies licenses, you also need to use the -t/--transitive flag.");
        }

        var latch = new CountDownLatch(artifacts.size());
        var anyError = new AtomicReference<ErrorCause>();
        var treeLogger = new DependencyTreeLogger(log, depsOptions);
        var depsCommandExecutor = createDepsCommandExecutor(options);

        depsCommandExecutor.fetchDependencyTree(
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

        latch.await();

        var errorCause = anyError.get();
        if (errorCause != null) {
            throw new JBuildException("Could not fetch all Maven dependencies successfully", errorCause);
        }
    }

    private void installArtifacts(Options options) throws Exception {
        var installOptions = InstallOptions.parse(options.commandArgs);
        var artifacts = parseArtifacts(installOptions.artifacts);

        if (artifacts.isEmpty()) {
            log.println("No artifacts were provided. Nothing to do.");
            return;
        }

        var fileWriter = installOptions.outDir == null
                ? new ArtifactFileWriter(new File(installOptions.repoDir), MAVEN_REPOSITORY)
                : new ArtifactFileWriter(new File(installOptions.outDir), FLAT_DIR);

        var installCommandExecutor = createInstallCommandExecutor(options, fileWriter);
        var latch = new CountDownLatch(1);
        var anyError = new AtomicReference<ErrorCause>();

        installCommandExecutor.installDependencyTree(
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
                    } else {
                        anyError.set(ErrorCause.ACTION_ERROR);
                    }
                } else {
                    anyError.set(err instanceof JBuildException
                            ? ((JBuildException) err).getErrorCause()
                            : ErrorCause.UNKNOWN);
                }
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await();
        } finally {
            fileWriter.close();
        }

        var errorCause = anyError.get();
        if (errorCause != null) {
            throw new JBuildException("Could not install all artifacts successfully", errorCause);
        }
    }

    private void fetchArtifacts(Options options) throws Exception {
        var fetchOptions = FetchOptions.parse(options.commandArgs);

        if (fetchOptions.artifacts.isEmpty()) {
            log.println("No artifacts were provided. Nothing to do.");
            return;
        }

        var artifacts = parseArtifacts(fetchOptions.artifacts);

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

        createFetchCommandExecutor(options).fetchArtifacts(artifacts, fileWriter)
                .forEach((artifact, completion) -> completion.whenComplete((ok, err) -> {
                    try {
                        reportErrors(anyError, artifact, ok, err);
                    } finally {
                        latch.countDown();
                    }
                }));

        try {
            latch.await();
        } finally {
            fileWriter.close();
        }

        var errorCause = anyError.get();
        if (errorCause != null) {
            throw new JBuildException("Could not fetch all artifacts successfully", errorCause);
        }

        log.verbosePrintln(() -> (artifacts.size() > 1 ? "All " + artifacts.size() + " artifacts" : "Artifact") +
                " successfully downloaded to " + fetchOptions.outDir);
    }

    private void listVersions(Options options) throws Exception {
        var commandExecutor = createVersionsCommandExecutor(options);
        var versionsOptions = VersionsOptions.parse(options.commandArgs);
        var artifacts = parseArtifacts(versionsOptions.artifacts);

        if (artifacts.isEmpty()) {
            log.println("No artifacts were provided. Nothing to do.");
            return;
        }

        var latch = new CountDownLatch(artifacts.size());
        var anyError = new AtomicReference<ErrorCause>();
        var metadataByArtifact = new ConcurrentSkipListMap<Artifact, MavenMetadata>(comparing(Artifact::getCoordinates));

        commandExecutor.getVersions(artifacts).forEach((artifact, eitherCompletionStage) ->
                eitherCompletionStage.whenComplete((completion, err) -> {
                    try {
                        if (err == null) {
                            completion.use(
                                    ok -> metadataByArtifact.put(artifact, ok),
                                    errors -> reportErrors(anyError, errors));
                        } else {
                            reportErrors(anyError, artifact, Optional.empty(), err);
                        }
                    } finally {
                        latch.countDown();
                    }
                }));

        latch.await();

        var versionLogger = new VersionLogger(log);
        metadataByArtifact.forEach(versionLogger::log);

        var errorCause = anyError.get();
        if (errorCause != null) {
            throw new JBuildException("Could not fetch all versions successfully", errorCause);
        }
    }

    private VersionsCommandExecutor createVersionsCommandExecutor(Options options) {
        if (options.repositories.isEmpty()) {
            return new VersionsCommandExecutor(log);
        }

        var baseUris = options.repositories.stream()
                .map(repo -> {
                    if (repo.startsWith("http://") || repo.startsWith("https://")) {
                        return URI.create(repo);
                    } else {
                        throw new JBuildException("the versions command only accepts HTTP(S) URIs, " +
                                "invalid repository value: " + repo, USER_INPUT);
                    }
                }).collect(Collectors.toList());

        return new VersionsCommandExecutor(log, NonEmptyCollection.of(baseUris), DefaultHttpClient.get());
    }

    private FetchCommandExecutor<ArtifactRetrievalError> createFetchCommandExecutor(Options options) {
        var retrievers = options.getRetrievers();
        if (retrievers.isEmpty()) {
            return FetchCommandExecutor.createDefault(log);
        }
        return createFetch(log, NonEmptyCollection.of(retrievers));
    }

    private static <E extends ArtifactRetrievalError> FetchCommandExecutor<E> createFetch(
            JBuildLog log,
            NonEmptyCollection<ArtifactRetriever<? extends E>> retrievers) {
        return new FetchCommandExecutor<>(log, retrievers);
    }

    private DepsCommandExecutor<ArtifactRetrievalError> createDepsCommandExecutor(Options options) {
        return DepsCommandExecutor.create(log, createFetchCommandExecutor(options));
    }

    private InstallCommandExecutor createInstallCommandExecutor(Options options,
                                                                ArtifactFileWriter writer) {
        return new InstallCommandExecutor(log,
                createFetchCommandExecutor(options),
                writer);
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
        log.println("The following errors have occurred:");
        for (var error : errors) {
            if (error instanceof JBuildException) {
                anyError.set(((JBuildException) error).getErrorCause());
                errRefSet = true;
                log.println(() -> "  * " + error.getMessage());
            } else {
                log.println(() -> "  * " + error);
            }
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
            e.printStackTrace(log.out);
            exitWithError(e.toString(), ErrorCause.UNKNOWN, startTime);
        }
        log.println(() -> "JBuild success in " + time(startTime) + "!");
        exit.accept(0);
    }

    private Set<? extends Artifact> parseArtifacts(Set<String> coordinates) {
        Set<Artifact> artifacts;
        try {
            artifacts = coordinates.stream()
                    .map(Artifact::parseCoordinates)
                    .collect(toSet());
        } catch (IllegalArgumentException e) {
            throw new JBuildException(e.getMessage(), USER_INPUT);
        }

        log.verbosePrintln(() -> "Parsed artifacts coordinates:" + LINE_END + artifacts.stream()
                .map(a -> "  * " + a + LINE_END)
                .collect(joining()));

        return artifacts;
    }

    private void exitWithError(String message, ErrorCause cause, long startTime) {
        log.print("ERROR: ");
        log.println(message);
        log.println(() -> "JBuild failed in " + time(startTime) +
                "! [error-type=" + cause.name().toLowerCase(Locale.ROOT) + "]");
        exit.accept(exitCode(cause));
    }

    private static CharSequence time(long startTime) {
        return durationText(Duration.ofMillis(System.currentTimeMillis() - startTime));
    }

    private static int exitCode(ErrorCause errorCause) {
        return errorCause.ordinal() + 1;
    }
}
