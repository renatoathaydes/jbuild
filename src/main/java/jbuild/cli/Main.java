package jbuild.cli;

import jbuild.Version;
import jbuild.api.JBuildException;
import jbuild.api.JBuildException.ErrorCause;
import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactRetriever;
import jbuild.artifact.file.ArtifactFileWriter;
import jbuild.artifact.file.MultiArtifactFileWriter;
import jbuild.commands.CompileCommandExecutor;
import jbuild.commands.DepsCommandExecutor;
import jbuild.commands.DoctorCommandExecutor;
import jbuild.commands.FetchCommandExecutor;
import jbuild.commands.InstallCommandExecutor;
import jbuild.commands.RequirementsCommandExecutor;
import jbuild.commands.VersionsCommandExecutor;
import jbuild.errors.ArtifactRetrievalError;
import jbuild.log.JBuildLog;
import jbuild.maven.MavenPom;
import jbuild.maven.MavenUtils;
import jbuild.util.Describable;
import jbuild.util.Executable;
import jbuild.util.FileUtils;
import jbuild.util.NonEmptyCollection;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static jbuild.api.JBuildException.ErrorCause.IO_WRITE;
import static jbuild.api.JBuildException.ErrorCause.USER_INPUT;
import static jbuild.artifact.file.ArtifactFileWriter.WriteMode.FLAT_DIR;
import static jbuild.artifact.file.ArtifactFileWriter.WriteMode.MAVEN_REPOSITORY;
import static jbuild.java.tools.Tools.verifyToolSuccessful;
import static jbuild.util.AsyncUtils.await;
import static jbuild.util.AsyncUtils.awaitValues;
import static jbuild.util.FileUtils.relativize;
import static jbuild.util.TextUtils.LINE_END;
import static jbuild.util.TextUtils.durationText;

public final class Main {

    static final String JBUILD_HEADER =
            "------ JBuild Basic CLI ------" + LINE_END +
                    "       Version: " + Version.VALUE + LINE_END +
                    "==============================" + LINE_END;

    static final String USAGE =
            "Utility to build Java (JVM) applications." + LINE_END +
                    LINE_END +
                    "Usage:" + LINE_END +
                    "    jbuild <root-options...> <cmd> <cmd-args...> " + LINE_END +
                    "Root Options:" + LINE_END +
                    "    --quiet" + LINE_END +
                    "     -q       print only minimum output." + LINE_END +
                    "    --repository" + LINE_END +
                    "     -r       Maven repository to use to locate artifacts (file location or HTTP URL)." + LINE_END +
                    "    --working-dir" + LINE_END +
                    "     -w       The working directory to use." + LINE_END +
                    "    --verbose" + LINE_END +
                    "    -V        log verbose output." + LINE_END +
                    "    --version" + LINE_END +
                    "    -v        print JBuild version and exit." + LINE_END +
                    "    --help" + LINE_END +
                    "    -h        print this usage message." + LINE_END +
                    LINE_END +
                    "Available commands:" + LINE_END +
                    LINE_END +
                    "  * " + CompileOptions.NAME + " - " + CompileOptions.DESCRIPTION + LINE_END +
                    "  * " + DepsOptions.NAME + " - " + DepsOptions.DESCRIPTION + LINE_END +
                    "  * " + DoctorOptions.NAME + " - " + DoctorOptions.DESCRIPTION + LINE_END +
                    "  * " + FetchOptions.NAME + " - " + FetchOptions.DESCRIPTION + LINE_END +
                    "  * " + InstallOptions.NAME + " - " + InstallOptions.DESCRIPTION + LINE_END +
                    "  * " + RequirementsOptions.NAME + " - " + RequirementsOptions.DESCRIPTION + LINE_END +
                    "  * " + VersionsOptions.NAME + " - " + VersionsOptions.DESCRIPTION + LINE_END +
                    "  * help - displays this help message or help for one of the other commands" + LINE_END +
                    LINE_END +
                    "Type 'jbuild help <command>' for more information about a command." + LINE_END + LINE_END +
                    "Artifact coordinates are given in the form <orgId>:<artifactId>[:<version>][:<ext>]" + LINE_END +
                    "If the version is omitted, the latest available version is normally used." + LINE_END + LINE_END +
                    "Examples:" + LINE_END +
                    "  # install latest version of Guava and all its dependencies in directory 'java-libs/'" + LINE_END +
                    "  jbuild install com.google.guava:guava" + LINE_END + LINE_END +
                    "  # show all version of javax.inject on the RedHat repository" + LINE_END +
                    "  jbuild -r https://maven.repository.redhat.com/ga/ versions javax.inject:javax.inject" + LINE_END + LINE_END +
                    "  # fetch the Guava POM" + LINE_END +
                    "  jbuild fetch com.google.guava:guava:31.0.1-jre:pom" + LINE_END + LINE_END +
                    "  # compile all Java sources in 'src/' or 'src/main/java' into a jar" + LINE_END +
                    "  # using all jars in 'java-libs/' as the classpath" + LINE_END +
                    "  jbuild compile";

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

        if (options.help || options.command.equals("help")) {
            showHelp(options);
            exit.accept(0);
            return;
        }

        if (options.version || options.command.equals("version")) {
            if (options.quiet) {
                log.println(Version.VALUE);
            } else {
                System.out.print(JBUILD_HEADER);
            }
            exit.accept(0);
            return;
        }

        withErrorHandling(() -> run(options), startTime, options.quiet);
    }

    private void run(Options options) throws Exception {
        if (options.command.isBlank()) {
            throw new JBuildException("No command given to execute. Run jbuild --help for usage.", USER_INPUT);
        }

        switch (options.command) {
            case CompileOptions.NAME:
                compile(options);
                break;
            case FetchOptions.NAME:
                fetchArtifacts(options);
                break;
            case DepsOptions.NAME:
                listDeps(options);
                break;
            case InstallOptions.NAME:
                installArtifacts(options);
                break;
            case DoctorOptions.NAME:
                doctor(options);
                break;
            case VersionsOptions.NAME:
                listVersions(options);
                break;
            case RequirementsOptions.NAME:
                requirements(options);
                break;
            default:
                throw new JBuildException("Unknown command: " + options.command +
                        ". Run jbuild --help for usage.", USER_INPUT);
        }
    }

    private void showHelp(Options options) {
        System.out.println(JBUILD_HEADER);
        if (options.commandArgs.isEmpty()) {
            System.out.println(USAGE);
        } else for (var arg : options.commandArgs) {
            if (arg.startsWith("-")) continue; // ignore flags
            switch (arg) {
                case CompileOptions.NAME:
                    System.out.println(CompileOptions.USAGE);
                    break;
                case DepsOptions.NAME:
                    System.out.println(DepsOptions.USAGE);
                    break;
                case DoctorOptions.NAME:
                    System.out.println(DoctorOptions.USAGE);
                    break;
                case FetchOptions.NAME:
                    System.out.println(FetchOptions.USAGE);
                    break;
                case InstallOptions.NAME:
                    System.out.println(InstallOptions.USAGE);
                    break;
                case RequirementsOptions.NAME:
                    System.out.println(RequirementsOptions.USAGE);
                    break;
                case VersionsOptions.NAME:
                    System.out.println(VersionsOptions.USAGE);
                    break;
                default:
                    System.out.println("Unknown command: " + arg);
            }
        }
    }

    private void compile(Options options) throws Exception {
        var compileOptions = CompileOptions.parse(options.commandArgs, options.quiet);

        var commandExecutor = new CompileCommandExecutor(log);

        var result = commandExecutor.compile(
                options.workingDir,
                compileOptions.inputDirectories, compileOptions.resourcesDirectories,
                compileOptions.outputDirOrJar, compileOptions.mainClass, compileOptions.groovyJar,
                compileOptions.generateJbManifest, compileOptions.createSourcesJar, compileOptions.createJavadocsJar,
                compileOptions.classpath, compileOptions.manifest, options.applicationArgs,
                compileOptions.incrementalChanges
        );
        result.getCompileResult().ifPresent(res -> verifyToolSuccessful("javac", res));
        result.getJarResult().ifPresent(jarResult -> verifyToolSuccessful("jar", jarResult));
        result.getJavadocJarResult().ifPresent(jarResult -> verifyToolSuccessful("javadocJar", jarResult));
        result.getSourcesJarResult().ifPresent(jarResult -> verifyToolSuccessful("sourcesJar", jarResult));
    }

    private void doctor(Options options) throws ExecutionException, InterruptedException {
        var docOptions = DoctorOptions.parse(options.commandArgs, !options.quiet);

        if (docOptions.entryPoints.isEmpty()) {
            log.println("No entry points provided, nothing to do. Please provide the entry points (jars) " +
                    "to check the classpath with the option -e, see usage for details.");
            return;
        }

        var commandExecutor = new DoctorCommandExecutor(log);

        commandExecutor.run(
                relativize(options.workingDir, docOptions.inputDir),
                docOptions.entryPoints, docOptions.typeExclusions
        ).toCompletableFuture().get();
    }

    private void listDeps(Options options) throws Exception {
        var depsOptions = DepsOptions.parse(options.commandArgs, !options.quiet);
        var artifacts = parseArtifacts(depsOptions.artifacts);

        if (artifacts.isEmpty() && depsOptions.pom.isBlank()) {
            log.println("No artifacts were provided. Nothing to do.");
            return;
        }
        MavenPom pom = null;
        if (!depsOptions.pom.isBlank()) {
            pom = MavenUtils.parsePom(Files.newInputStream(Paths.get(depsOptions.pom)));
        }

        if (depsOptions.licenses && !depsOptions.transitive) {
            // we cannot fetch dependencies' POMs to see their licenses unless we fetch transitive dependencies
            log.println(() -> "WARNING: to display dependencies licenses, you also need to use the -t/--transitive flag.");
        }

        var treeLogger = new DependencyTreeLogger(log, depsOptions);
        var depsCommandExecutor = createDepsCommandExecutor(options);
        var anyError = new AtomicReference<ErrorCause>();

        List<CompletionStage<Void>> allStages = depsCommandExecutor.fetchDependencyTree(
                        artifacts, pom, depsOptions.scopes, depsOptions.transitive, depsOptions.optional,
                        depsOptions.exclusions)
                .entrySet().stream().map(entry -> {
                    var artifact = entry.getKey();
                    var treeCompletion = entry.getValue();
                    return treeCompletion.thenApplyAsync(maybeTree -> {
                        // Optional.empty means there was an error resolving this dep!
                        if (maybeTree.isEmpty()) {
                            return false;
                        }
                        treeLogger.logTree(maybeTree.get());
                        return true;
                    }).handle((ok, err) -> {
                        if (err != null) reportErrors(anyError, artifact, err);
                        else if (!ok) reportErrors(anyError, artifact, null);
                        return (Void) null;
                    });
                }).collect(toList());

        await(awaitValues(allStages), Duration.ofSeconds(10),
                "Resolve dependencies and log dependency tree");

        var errorCause = anyError.get();
        if (errorCause != null) {
            throw new JBuildException("Could not fetch all dependencies successfully", errorCause);
        }
    }

    private void installArtifacts(Options options) throws Exception {
        var installOptions = InstallOptions.parse(options.commandArgs, !options.quiet);
        var artifacts = parseArtifacts(installOptions.artifacts);

        if (artifacts.isEmpty()) {
            log.println("No artifacts were provided. Nothing to do.");
            return;
        }

        var fileWriter = selectArtifactWriter(options.workingDir, installOptions);

        var installCommandExecutor = createInstallCommandExecutor(options, fileWriter);
        var latch = new CountDownLatch(1);
        var anyError = new AtomicReference<ErrorCause>();

        installCommandExecutor.installDependencyTree(
                artifacts, installOptions.scopes, installOptions.optional, installOptions.transitive,
                installOptions.exclusions, installOptions.checksum
        ).whenComplete((successCount, err) -> {
            try {
                if (err == null) {
                    var successes = successCount.map(ok -> ok, errors -> {
                        reportErrors(anyError, errors);
                        return -1L;
                    });
                    if (successes > 0) {
                        log.println(() -> "Successfully installed " + successes +
                                " artifact" + (successes == 1 ? "" : "s") + " at " + fileWriter.getDestination());
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
        var fetchOptions = FetchOptions.parse(options.commandArgs, !options.quiet);

        if (fetchOptions.artifacts.isEmpty()) {
            log.println("No artifacts were provided. Nothing to do.");
            return;
        }

        var artifacts = parseArtifacts(fetchOptions.artifacts);

        var outDir = new File(relativize(options.workingDir, fetchOptions.outDir));

        var dirExists = FileUtils.ensureDirectoryExists(outDir);
        if (!dirExists) {
            throw new JBuildException(
                    "Output directory does not exist and cannot be created: " + outDir.getPath(),
                    IO_WRITE);
        }

        var fileWriter = new ArtifactFileWriter(outDir, FLAT_DIR);
        var latch = new CountDownLatch(artifacts.size());
        var anyError = new AtomicReference<ErrorCause>();

        createFetchCommandExecutor(options).fetchArtifacts(artifacts, fileWriter, true)
                .forEach((artifact, completion) -> completion.whenComplete((ok, err) -> {
                    try {
                        if (err != null || ok.isEmpty()) {
                            reportErrors(anyError, artifact, err);
                        }
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
                " successfully downloaded to " + outDir);
    }

    private void listVersions(Options options) throws Exception {
        var commandExecutor = createVersionsCommandExecutor(options);
        var versionsOptions = VersionsOptions.parse(options.commandArgs, !options.quiet);
        var artifacts = parseArtifacts(versionsOptions.artifacts);

        if (artifacts.isEmpty()) {
            log.println("No artifacts were provided. Nothing to do.");
            return;
        }

        var versionLogger = new VersionLogger(log);
        var latch = new CountDownLatch(artifacts.size());
        var anyError = new AtomicReference<ErrorCause>();

        commandExecutor.getVersions(artifacts).forEach((artifact, eitherCompletionStage) ->
                eitherCompletionStage.whenComplete((completion, err) -> {
                    try {
                        if (err == null) {
                            completion.use(
                                    ok -> versionLogger.log(artifact, ok),
                                    errors -> reportErrors(anyError, errors));
                        } else {
                            reportErrors(anyError, artifact, err);
                        }
                    } finally {
                        latch.countDown();
                    }
                }));

        latch.await();

        var errorCause = anyError.get();
        if (errorCause != null) {
            throw new JBuildException("Could not fetch all versions successfully", errorCause);
        }
    }

    private void requirements(Options options) {
        var command = RequirementsCommandExecutor.createDefault(log);
        var reqOptions = RequirementsOptions.parse(options.commandArgs, !options.quiet);
        await(command.execute(relativize(options.workingDir, reqOptions.files), reqOptions.perClass),
                Duration.ofMinutes(2),
                "requirements");
    }

    private VersionsCommandExecutor createVersionsCommandExecutor(Options options) {
        var retrievers = options.getRetrievers(options.workingDir, log);
        if (retrievers.isEmpty()) {
            return new VersionsCommandExecutor(log);
        }
        return new VersionsCommandExecutor(log, NonEmptyCollection.of(retrievers));
    }

    private FetchCommandExecutor<ArtifactRetrievalError> createFetchCommandExecutor(Options options) {
        var retrievers = options.getRetrievers(options.workingDir, log);
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
                              Throwable err) {
        log.print(() -> "An error occurred while processing " + artifact.getCoordinates());
        if (err instanceof JBuildException) {
            log.println(": " + err.getMessage());
            anyError.set(((JBuildException) err).getErrorCause());
        } else {
            log.println(err == null ? "" : (": " + err));
            anyError.set(ErrorCause.UNKNOWN);
        }
    }

    private void reportErrors(AtomicReference<ErrorCause> anyError, NonEmptyCollection<?> errors) {
        var errRefSet = false;
        log.println("The following errors have occurred:");
        for (var error : errors) {
            if (error instanceof JBuildException) {
                anyError.set(((JBuildException) error).getErrorCause());
                errRefSet = true;
                log.println(() -> "  * " + ((JBuildException) error).getMessage());
            } else if (error instanceof Describable) {
                log.println(() -> "  * " + ((Describable) error).getDescription());
            } else {
                log.println(() -> "  * " + error);
            }
        }
        if (!errRefSet && anyError.get() == null) {
            anyError.set(ErrorCause.UNKNOWN);
        }
    }

    private void withErrorHandling(Executable exe, long startTime, boolean quiet) {
        var isError = true;
        try {
            exe.run();
            isError = false;
        } catch (JBuildException e) {
            exitWithError(e.getMessage(), e.getErrorCause(), startTime, quiet);
        } catch (ExecutionException e) {
            var cause = e.getCause();
            if (cause instanceof JBuildException) {
                exitWithError(cause.getMessage(), ((JBuildException) cause).getErrorCause(), startTime, quiet);
            }
            e.printStackTrace(log.out);
            exitWithError(e.toString(), ErrorCause.UNKNOWN, startTime, quiet);
        } catch (Exception e) {
            e.printStackTrace(log.out);
            exitWithError(e.toString(), ErrorCause.UNKNOWN, startTime, quiet);
        }
        if (!isError && !quiet) {
            log.println(() -> "JBuild success in " + time(startTime) + "!");
        }
        exit.accept(0);
    }

    private Set<? extends Artifact> parseArtifacts(Set<String> coordinates) {
        Set<Artifact> artifacts = coordinates.stream()
                .map(Artifact::parseCoordinates)
                .collect(toSet());

        if (!artifacts.isEmpty()) {
            log.verbosePrintln(() -> "Parsed artifacts coordinates:" + LINE_END + artifacts.stream()
                    .map(a -> "  * " + a + LINE_END)
                    .collect(joining()));
        }

        return artifacts;
    }

    private void exitWithError(String message, ErrorCause cause, long startTime, boolean quiet) {
        log.print("ERROR: ");
        log.println(message);
        if (!quiet) {
            log.println(() -> "JBuild failed in " + time(startTime) +
                    "! [error-type=" + cause.name().toLowerCase(Locale.ROOT) + "]");
        }
        exit.accept(exitCode(cause));
    }

    private static ArtifactFileWriter selectArtifactWriter(
            String workingDir, InstallOptions installOptions) {
        var writer = installOptions.outDir == null
                ? new ArtifactFileWriter(new File(relativize(workingDir, installOptions.repoDir)), MAVEN_REPOSITORY)
                : new ArtifactFileWriter(new File(relativize(workingDir, installOptions.outDir)), FLAT_DIR);

        if (installOptions.mavenLocal) {
            var m2Repo = MavenUtils.mavenHome().toAbsolutePath();
            var mavenRepoWriter = new ArtifactFileWriter(m2Repo.toFile(), MAVEN_REPOSITORY);
            return new MultiArtifactFileWriter(writer, mavenRepoWriter);
        }

        return writer;
    }

    private static CharSequence time(long startTime) {
        return durationText(Duration.ofMillis(System.currentTimeMillis() - startTime));
    }

    private static int exitCode(ErrorCause errorCause) {
        return errorCause.ordinal() + 1;
    }
}
