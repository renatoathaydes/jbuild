package jbuild.commands;

import jbuild.api.JBuildException;
import jbuild.extension.JbManifestGenerator;
import jbuild.java.tools.CreateJarOptions;
import jbuild.java.tools.CreateJarOptions.FileSet;
import jbuild.java.tools.GroovyCompiler;
import jbuild.java.tools.ToolRunResult;
import jbuild.java.tools.Tools;
import jbuild.log.JBuildLog;
import jbuild.util.Either;
import jbuild.util.FileCollection;
import jbuild.util.FileUtils;
import jbuild.util.JarFileFilter;
import jbuild.util.JarPatcher;
import jbuild.util.NoOp;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.completedStage;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static jbuild.api.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.api.JBuildException.ErrorCause.IO_READ;
import static jbuild.api.JBuildException.ErrorCause.IO_WRITE;
import static jbuild.api.JBuildException.ErrorCause.USER_INPUT;
import static jbuild.util.AsyncUtils.await;
import static jbuild.util.AsyncUtils.awaitSuccessValues;
import static jbuild.util.AsyncUtils.runAsyncTiming;
import static jbuild.util.CollectionUtils.appendAsStream;
import static jbuild.util.FileUtils.collectFiles;
import static jbuild.util.FileUtils.ensureDirectoryExists;
import static jbuild.util.FileUtils.relativize;
import static jbuild.util.TextUtils.ensureEndsWith;

public final class CompileCommandExecutor {

    private static final FilenameFilter JAVA_FILES_FILTER = (dir, name) ->
            name.endsWith(".java");
    private static final FilenameFilter JAVA_GROOVY_FILES_FILTER = (dir, name) ->
            name.endsWith(".java") || name.endsWith(".groovy");
    private static final FilenameFilter NON_JAVA_FILES_FILTER = (dir, name) ->
            !name.endsWith(".java");
    private static final FilenameFilter NON_JAVA_GROOVY_FILES_FILTER = (dir, name) ->
            !name.endsWith(".java") && !name.endsWith(".groovy");
    private static final FilenameFilter ALL_FILES_FILTER = (dir, name) -> true;
    private static final Pattern NOT_EMPTY_WORD = Pattern.compile("[a-zA-Z0-9]");

    private final JBuildLog log;

    public CompileCommandExecutor(JBuildLog log) {
        this.log = log;
    }

    public CompileCommandResult compile(Set<String> inputDirectories,
                                        Set<String> resourcesDirectories,
                                        Either<String, String> outputDirOrJar,
                                        String mainClass,
                                        String groovyJar,
                                        boolean generateJbManifest,
                                        String classpath,
                                        Either<Boolean, String> manifest,
                                        List<String> compilerArgs,
                                        IncrementalChanges incrementalChanges)
            throws InterruptedException, ExecutionException {
        return compile(".", inputDirectories, resourcesDirectories,
                outputDirOrJar, mainClass, groovyJar, generateJbManifest, false, false,
                classpath, manifest, compilerArgs, incrementalChanges);
    }

    public CompileCommandResult compile(String workingDir,
                                        Set<String> inputDirectories,
                                        Set<String> resourcesDirectories,
                                        Either<String, String> outputDirOrJar,
                                        String mainClass,
                                        String groovyJar,
                                        boolean generateJbManifest,
                                        boolean createSourcesJar,
                                        boolean createJavadocsJar,
                                        String classpath,
                                        Either<Boolean, String> manifest,
                                        List<String> compilerArgs,
                                        IncrementalChanges incrementalChanges)
            throws InterruptedException, ExecutionException {
        var usingDefaultInputDirs = inputDirectories.isEmpty() && incrementalChanges == null;
        if (usingDefaultInputDirs) {
            inputDirectories = computeDefaultSourceDirs(workingDir);
        } else {
            inputDirectories = relativize(workingDir, inputDirectories);
        }
        if (resourcesDirectories.isEmpty()) {
            resourcesDirectories = computeDefaultResourceDirs(workingDir);
        } else {
            resourcesDirectories = relativize(workingDir, resourcesDirectories);
        }
        if (incrementalChanges != null) {
            incrementalChanges = incrementalChanges.relativize(workingDir);
        }
        boolean includeGroovy = !groovyJar.isBlank();
        var sourceFiles = computeSourceFiles(inputDirectories, incrementalChanges, includeGroovy);
        if (incrementalChanges == null && sourceFiles.isEmpty()) {
            var lookedAt = usingDefaultInputDirs
                    ? "src/main/java/, src/ and '.'"
                    : String.join(", ", inputDirectories);
            throw new JBuildException("No source files found " +
                    "(directories tried: " + lookedAt + ")", USER_INPUT);
        }

        var resourceFiles = computeResourceFiles(inputDirectories, resourcesDirectories, incrementalChanges, includeGroovy);

        if (log.isVerbose() && incrementalChanges == null) {
            log.verbosePrintln("Found " + sourceFiles.size() + " source file(s) to compile.");
            if (resourceFiles.isEmpty()) {
                log.verbosePrintln("No resource files found.");
            } else {
                log.verbosePrintln("Found " + resourceFiles.stream()
                        .map(col -> col.files.size())
                        .reduce(0, Integer::sum) + " resource file(s).");
            }
        }

        // if the output is a Jar, send the class files to a temp folder.
        var outputDir = outputDirOrJar.map(
                outDir -> relativize(workingDir, outDir),
                jar -> getTempDirectory());

        log.verbosePrintln(() -> "Compilation output will be sent to " + outputDir);

        // jarFile will be null if the output is not a Jar.
        var jarFile = outputDirOrJar.map(NoOp.fun(), jar -> jarOrDefault(workingDir, jar));

        var deletionsDone = false;
        if (incrementalChanges != null && !incrementalChanges.deletedFiles.isEmpty()) {
            var isJar = jarFile != null;
            var deletions = computeDeletedFiles(
                    inputDirectories, resourcesDirectories, incrementalChanges.deletedFiles, isJar, outputDir);
            if (!deletions.isEmpty()) {
                if (isJar) {
                    deleteFilesFromJar(deletions, jarFile);
                } else {
                    deleteFilesFromDir(deletions, outputDir);
                }
                deletionsDone = true;
            }
        }

        if (sourceFiles.isEmpty() && resourceFiles.isEmpty()) {
            if (!deletionsDone) {
                log.println("No sources to compile. No resource files found. Nothing to do!");
            }
            return new CompileCommandResult();
        }

        if (!ensureDirectoryExists(new File(outputDir))) {
            throw new JBuildException(outputDir + " directory could not be created", IO_WRITE);
        }

        copyResources(resourceFiles, outputDir);

        var computedClasspath = computeClasspath(relativize(workingDir, classpath),
                incrementalChanges == null ? null : jarFile == null ? outputDir : jarFile);

        ToolRunResult compileResult = null;
        if (sourceFiles.isEmpty()) {
            log.println("No source files to compile");
            if (resourceFiles.isEmpty()) {
                return new CompileCommandResult();
            }
        } else {
            JbuildCompiler compiler = groovyJar.isBlank()
                    ? Tools.Javac.create(log)
                    : new GroovyCompiler(log, relativize(workingDir, groovyJar));
            compileResult = await(runAsyncTiming(() -> compiler
                                    .compile(sourceFiles, outputDir, computedClasspath, compilerArgs),
                            createLogTimer("Compilation successful on directory '" + outputDir + "'")),
                    Duration.ofMinutes(30),
                    "compile");
            if (compileResult.exitCode() != 0) {
                return new CompileCommandResult(compileResult);
            }
        }

        if (generateJbManifest) {
            log.verbosePrintln("Generating jb manifest file");
            var generator = new JbManifestGenerator(log);
            var jbManifest = generator.generateJbManifest(outputDir, jarFile, incrementalChanges);
            copyResources(List.of(jbManifest), outputDir);
        }

        if (jarFile == null) {
            return new CompileCommandResult(compileResult);
        }

        var jarResults = invokeJarTasks(inputDirectories, mainClass, createSourcesJar, createJavadocsJar,
                incrementalChanges, outputDir, jarFile, manifest, computedClasspath, sourceFiles);

        return new CompileCommandResult(compileResult,
                jarResults.next(), jarResults.next(), jarResults.next());
    }

    private Iterator<ToolRunResult> invokeJarTasks(Set<String> inputDirectories,
                                                   String mainClass,
                                                   boolean createSourcesJar,
                                                   boolean createJavadocsJar,
                                                   IncrementalChanges incrementalChanges,
                                                   String outputDir,
                                                   String jarFile,
                                                   Either<Boolean, String> manifest,
                                                   String computedClasspath,
                                                   Set<String> sourceFiles) {
        // make sure the tmp directory exists as the Jar command assumes it does!
        try {
            Files.createTempFile("CompileCommandExecutor", "");
        } catch (IOException e) {
            log.println("WARNING: unable to create temp file, jar command may fail!");
        }

        List<CompletionStage<ToolRunResult>> actions = List.of(
                jar(mainClass, outputDir, jarFile, manifest, incrementalChanges),
                createSourcesJar
                        ? sourcesJar(inputDirectories, jarFile)
                        : completedStage(null),
                createJavadocsJar
                        ? createJavadoc(computedClasspath, sourceFiles)
                        .thenCompose(result -> javadocJar(result, jarFile))
                        : completedStage(null));

        var timeout = Duration.ofMinutes(5);

        return await(
                awaitSuccessValues(actions),
                timeout,
                "jar, sources-jar, javadocs-jar)"
        ).iterator();
    }

    private CompletionStage<Either<ToolRunResult, String>> createJavadoc(String classpath, Set<String> sourceFiles) {
        String outputDir;
        try {
            outputDir = Files.createTempDirectory("jbuild-javadocs-").toString();
        } catch (IOException e) {
            return CompletableFuture.failedFuture(
                    new JBuildException("Unable to create temp dir for javadocs: " + e, IO_WRITE));
        }
        return supplyAsync(() -> {
            var startTime = System.currentTimeMillis();
            var toolResult = Tools.Javadoc.create().createJavadoc(classpath, sourceFiles, outputDir);
            if (toolResult.exitCode() == 0) {
                log.verbosePrintln(() -> "Created javadoc in " + (System.currentTimeMillis() - startTime) + " ms");
                return Either.right(outputDir);
            }
            return Either.left(toolResult);
        });
    }

    private CompletionStage<ToolRunResult> jar(String mainClass,
                                               String outputDir,
                                               String jarFile,
                                               Either<Boolean, String> manifest,
                                               IncrementalChanges incrementalChanges) {
        var jarContent = new FileSet(Set.of(), outputDir);

        if (incrementalChanges == null) {
            var jarOptions = new CreateJarOptions(
                    jarFile, mainClass, manifest, "", jarContent, Map.of());
            log.verbosePrintln(() -> "Creating jar file at " + jarFile + ". Full command: jar " +
                    String.join(" ", jarOptions.toArgs(true)));
            return runAsyncTiming(() -> Tools.Jar.create().createJar(jarOptions),
                    createLogTimer("Created jar"));
        }

        log.verbosePrintln(() -> "Updating jar file at " + jarFile);
        return runAsyncTiming(() -> Tools.Jar.create().updateJar(jarFile, jarContent),
                createLogTimer("Updated jar"));
    }

    private CompletionStage<ToolRunResult> sourcesJar(Set<String> inputDirs, String jarFile) {
        var sourcesJar = FileUtils.withoutExtension(jarFile) + "-sources.jar";
        log.verbosePrintln(() -> "Creating sources jar file at " + sourcesJar);
        return runAsyncTiming(() -> Tools.Jar.create().createJar(sourcesJar, inputDirs),
                createLogTimer("Created sources jar"));
    }

    private CompletionStage<ToolRunResult> javadocJar(Either<ToolRunResult, String> javadocResult, String jarFile) {
        return javadocResult.map(CompletableFuture::completedFuture, inputDir -> {
            var javadocJar = FileUtils.withoutExtension(jarFile) + "-javadoc.jar";
            log.verbosePrintln(() -> "Creating javadoc jar file at " + javadocJar);
            return runAsyncTiming(() -> Tools.Jar.create().createJar(javadocJar, Set.of(inputDir)),
                    createLogTimer("Created javadoc jar"));
        });
    }

    private BiConsumer<Duration, ToolRunResult> createLogTimer(String messagePrefix) {
        return (duration, result) -> {
            if (result.exitCode() == 0) {
                log.verbosePrintln(() -> messagePrefix + " in " + duration.toMillis() + " ms");
            }
        };
    }

    private Set<String> computeSourceFiles(Set<String> inputDirectories,
                                           IncrementalChanges incrementalChanges,
                                           boolean includeGroovy) {
        Predicate<String> includeFile = includeGroovy
                ? name -> JAVA_GROOVY_FILES_FILTER.accept(null, name)
                : name -> JAVA_FILES_FILTER.accept(null, name);
        if (incrementalChanges != null) {
            var sourceFiles = incrementalChanges.addedFiles.stream()
                    .filter(includeFile)
                    .collect(toSet());
            if (!sourceFiles.isEmpty()) {
                log.verbosePrintln(() -> "Compiling " + incrementalChanges.addedFiles.size()
                        + " files added or modified since last compilation");
            }
            return sourceFiles;
        }
        return collectFiles(inputDirectories, includeGroovy ? JAVA_GROOVY_FILES_FILTER : JAVA_FILES_FILTER).stream()
                .flatMap(col -> col.files.stream())
                .collect(toSet());
    }

    private Set<String> computeDeletedFiles(Set<String> inputDirectories,
                                            Set<String> resourcesDirectories,
                                            Set<String> deletedFiles,
                                            boolean forJar,
                                            String outputDir) {
        var directories = new HashSet<String>(inputDirectories.size() + resourcesDirectories.size());
        var separator = forJar ? '/' : File.separatorChar;
        // if we're making paths for a Jar, we need to change the paths, but only on Windows
        // because on Windows the path separator is `\` but on jars (and other systems) it's `/`.
        var fixWindowsPaths = forJar && File.separatorChar == '\\';
        for (String resourcesDirectory : inputDirectories) {
            directories.add(ensureEndsWith(resourcesDirectory, separator).replaceAll("\\\\", "/"));
        }
        for (String resourcesDirectory : resourcesDirectories) {
            directories.add(ensureEndsWith(resourcesDirectory, separator).replaceAll("\\\\", "/"));
        }
        var result = new HashSet<String>(deletedFiles.size());
        for (String deletedFile : deletedFiles) {
            final var file = fixWindowsPaths ? deletedFile.replaceAll("\\\\", "/") : deletedFile;
            if (file.endsWith(".class")) {
                // class files must be passed with the exact output path (if not forJar), unlike resources
                if (forJar) {
                    result.add(file);
                } else {
                    result.add(relativize(outputDir, file));
                }
            } else {
                // resource files need to be made relative
                var found = false;
                for (var dir : directories) {
                    if (file.startsWith(dir)) {
                        result.add(file.substring(dir.length()));
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    log.println(() -> "WARNING: cannot delete resource as it is not under any resource directory: " + file);
                }
            }
        }
        if (!result.isEmpty()) {
            log.verbosePrintln(() -> "Deleting " + result.size() + " file" +
                    (result.size() == 1 ? "" : "s") + " from output");
        }
        return result;
    }

    private List<FileCollection> computeResourceFiles(Set<String> inputDirectories,
                                                      Set<String> resourcesDirectories,
                                                      IncrementalChanges incrementalChanges,
                                                      boolean includeGroovy) {
        if (incrementalChanges != null) {
            var resourcesByDir = new HashMap<String, List<String>>(
                    inputDirectories.size() + resourcesDirectories.size());
            for (String resourcesDirectory : inputDirectories) {
                resourcesByDir.put(ensureEndsWith(resourcesDirectory, File.separatorChar), new ArrayList<>());
            }
            for (String resourcesDirectory : resourcesDirectories) {
                resourcesByDir.put(ensureEndsWith(resourcesDirectory, File.separatorChar), new ArrayList<>());
            }
            for (String file : incrementalChanges.addedFiles) {
                if (!file.endsWith(".java")) {
                    String dir = null;
                    for (var resourceDir : resourcesByDir.keySet()) {
                        if (file.startsWith(resourceDir)) {
                            dir = resourceDir;
                            break;
                        }
                    }
                    if (dir == null) {
                        log.println(() -> "WARNING: ignoring resource as it is not under any resource directory: " + file);
                    } else {
                        resourcesByDir.get(dir).add(file);
                    }
                }
            }
            var resourceCount = resourcesByDir.values().stream()
                    .mapToInt(Collection::size)
                    .sum();
            if (resourceCount > 0) {
                log.verbosePrintln(() -> "Including " + resourceCount + " added or modified resource files on output");
                return resourcesByDir.entrySet().stream()
                        .filter(e -> !e.getValue().isEmpty())
                        .map(e -> new FileCollection(e.getKey(), e.getValue()))
                        .collect(toList());
            }
            return List.of();
        }
        return appendAsStream(
                collectFiles(inputDirectories, includeGroovy ? NON_JAVA_GROOVY_FILES_FILTER : NON_JAVA_FILES_FILTER),
                collectFiles(resourcesDirectories, ALL_FILES_FILTER)).collect(toList());
    }

    private void deleteFilesFromDir(Set<String> deletedFiles, String outputDir) {
        var dir = Paths.get(outputDir);
        if (!dir.toFile().isDirectory()) {
            throw new JBuildException("The outputDir does not exist, cannot delete files from it", USER_INPUT);
        }
        for (var file : deletedFiles) {
            var toDelete = dir.resolve(file);
            if (!toDelete.toFile().delete()) {
                log.println("WARNING: could not delete file: " + toDelete);
            }
        }
    }

    private void deleteFilesFromJar(Set<String> deletedFiles, String jarFile) {
        var startTime = log.isVerbose() ? System.currentTimeMillis() : 0L;
        try {
            JarPatcher.deleteFromJar(new File(jarFile), deletedFiles);
        } catch (IOException e) {
            throw new JBuildException("Could not patch existing jar file '" + jarFile + "' due to: " + e, IO_WRITE);
        }
        if (log.isVerbose()) {
            log.verbosePrintln("Deleted files from jar in " + (System.currentTimeMillis() - startTime) + "ms");
        }
    }

    private void copyResources(List<FileCollection> resourceFiles, String outputDir) {
        if (resourceFiles.isEmpty()) return;
        var outPath = Paths.get(outputDir);
        int totalResources = 0;
        try {
            for (var resourceCollection : resourceFiles) {
                var srcDir = Paths.get(resourceCollection.directory);
                for (var resource : resourceCollection.files) {
                    var resourceFile = new File(resource);
                    var destination = outPath.resolve(srcDir.relativize(Paths.get(resource)));
                    var resourceDir = destination.getParent().toFile();
                    if (!resourceDir.mkdirs() && !resourceDir.isDirectory()) {
                        throw new JBuildException(resourceDir + " directory could not be created", IO_WRITE);
                    }
                    Files.copy(resourceFile.toPath(), destination,
                            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    totalResources++;
                }
            }
            final var total = totalResources;
            log.verbosePrintln(() -> "Copied " + total + " resource(s) into " + outputDir);
        } catch (IOException e) {
            throw new JBuildException("Error copying resources: " + e, IO_WRITE);
        }
    }

    private static String computeClasspath(String classpath,
                                           String previousOutput) {
        return Stream.concat(
                        previousOutput == null ? Stream.of() : Stream.of(previousOutput),
                        Stream.of(classpath.split(File.pathSeparator))
                                .map(CompileCommandExecutor::computeClasspathPart)
                                .filter(not(String::isBlank)))
                .collect(joining(File.pathSeparator));
    }

    private static String computeClasspathPart(String classpath) {
        if (classpath.isBlank()) {
            return "";
        }
        if (classpath.endsWith("*")) {
            return classpath;
        }
        File canonical;
        try {
            canonical = new File(classpath).getCanonicalFile();
        } catch (IOException e) {
            throw new JBuildException("Cannot compute classpath, unable to canonicalize " + classpath +
                    " due to " + e, IO_READ);
        }
        if (canonical.isDirectory()) {
            // expand classpath to include any jars available in the directory
            var jars = FileUtils.allFilesInDir(canonical, JarFileFilter.getInstance());
            if (jars.length > 0) {
                return Stream.concat(Stream.of(classpath), Stream.of(jars).map(File::getPath))
                        .collect(joining(File.pathSeparator));
            }
        }
        return canonical.getPath();
    }

    String jarOrDefault(String workingDir, String jar) {
        String targetJar;
        if (jar.isBlank()) {
            // check if the custom workingDir is a non-empty word of some sort
            if (NOT_EMPTY_WORD.matcher(workingDir).find()) {
                targetJar = "build" + File.separatorChar + new File(workingDir).getName() + ".jar";
                log.verbosePrintln(() -> "Using default jar name based on working dir: " + targetJar);
            } else {
                var dir = System.getProperty("user.dir");
                if (dir != null) {
                    targetJar = "build" + File.separatorChar + new File(dir).getName() + ".jar";
                    log.verbosePrintln(() -> "Using default jar name based on user.dir: " + targetJar);
                } else {
                    log.verbosePrintln("Using default jar name: lib.jar");
                    targetJar = "lib.jar";
                }
            }
        } else {
            targetJar = jar;
        }
        return relativize(workingDir, targetJar);
    }

    private String getTempDirectory() {
        try {
            return Files.createTempDirectory("jbuild-compile").toFile().getAbsolutePath();
        } catch (IOException e) {
            throw new JBuildException("Could not create temp directory for compiled files", ACTION_ERROR);
        }
    }

    private Set<String> computeDefaultSourceDirs(String workingDir) {
        var srcMainJava = Paths.get(workingDir, "src", "main", "java");
        if (srcMainJava.toFile().isDirectory()) {
            log.verbosePrintln(() -> "Using source directory: " + srcMainJava);
            return Set.of(srcMainJava.toString());
        }
        var src = Paths.get(workingDir, "src");
        if (src.toFile().isDirectory()) {
            log.verbosePrintln(() -> "Using source directory: " + src);
            return Set.of(src.toString());
        }
        log.verbosePrintln(() -> "Using working directory as source directory: " + Paths.get(workingDir).toAbsolutePath());
        return Set.of(".");
    }

    private Set<String> computeDefaultResourceDirs(String workingDir) {
        var srcMainResources = Paths.get(workingDir, "src", "main", "resources");
        if (srcMainResources.toFile().isDirectory()) {
            log.verbosePrintln(() -> "Using resource directory: " + srcMainResources);
            return Set.of(srcMainResources.toString());
        }
        var res = Paths.get(workingDir, "resources");
        if (res.toFile().isDirectory()) {
            log.verbosePrintln(() -> "Using resource directory: " + res);
            return Set.of(res.toString());
        }
        log.verbosePrintln("No resource directory found.");
        return Set.of();
    }

    public static final class CompileCommandResult {
        private final ToolRunResult compileResult;
        private final ToolRunResult jarResult;
        private final ToolRunResult sourcesJarResult;
        private final ToolRunResult javadocJarResult;

        public CompileCommandResult(ToolRunResult compileResult,
                                    ToolRunResult jarResult,
                                    ToolRunResult sourcesJarResult,
                                    ToolRunResult javadocJarResult) {
            this.compileResult = compileResult;
            this.jarResult = jarResult;
            this.sourcesJarResult = sourcesJarResult;
            this.javadocJarResult = javadocJarResult;
        }

        public CompileCommandResult() {
            this(null, null, null, null);
        }

        public CompileCommandResult(ToolRunResult compileResult) {
            this(compileResult, null, null, null);
        }

        public boolean isSuccessful() {
            return (compileResult == null || compileResult.exitCode() == 0)
                    && (jarResult == null || jarResult.exitCode() == 0)
                    && (sourcesJarResult == null || sourcesJarResult.exitCode() == 0)
                    && (javadocJarResult == null || javadocJarResult.exitCode() == 0);
        }

        public Optional<ToolRunResult> getCompileResult() {
            return Optional.ofNullable(compileResult);
        }

        public Optional<ToolRunResult> getJarResult() {
            return Optional.ofNullable(jarResult);
        }

        public Optional<ToolRunResult> getSourcesJarResult() {
            return Optional.ofNullable(sourcesJarResult);
        }

        public Optional<ToolRunResult> getJavadocJarResult() {
            return Optional.ofNullable(javadocJarResult);
        }
    }

}
