package jbuild.commands;

import jbuild.api.JBuildException;
import jbuild.java.tools.CreateJarOptions;
import jbuild.java.tools.CreateJarOptions.FileSet;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static jbuild.api.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.api.JBuildException.ErrorCause.IO_WRITE;
import static jbuild.api.JBuildException.ErrorCause.USER_INPUT;
import static jbuild.util.CollectionUtils.appendAsStream;
import static jbuild.util.FileUtils.collectFiles;
import static jbuild.util.FileUtils.ensureDirectoryExists;
import static jbuild.util.FileUtils.relativize;
import static jbuild.util.TextUtils.ensureEndsWith;

public final class CompileCommandExecutor {

    private static final FilenameFilter JAVA_FILES_FILTER = (dir, name) -> name.endsWith(".java");
    private static final FilenameFilter NON_JAVA_FILES_FILTER = (dir, name) -> !name.endsWith(".java");
    private static final FilenameFilter ALL_FILES_FILTER = (dir, name) -> true;
    private static final Pattern DEFAULT_JAR_FROM_WORKING_DIR = Pattern.compile("[a-zA-Z0-9]");

    private final JBuildLog log;

    public CompileCommandExecutor(JBuildLog log) {
        this.log = log;
    }

    public CompileCommandResult compile(Set<String> inputDirectories,
                                        Set<String> resourcesDirectories,
                                        Either<String, String> outputDirOrJar,
                                        String mainClass,
                                        boolean generateJbManifest,
                                        String classpath,
                                        List<String> compilerArgs,
                                        IncrementalChanges incrementalChanges) {
        return compile(".", inputDirectories, resourcesDirectories,
                outputDirOrJar, mainClass, generateJbManifest, classpath, compilerArgs, incrementalChanges);
    }

    public CompileCommandResult compile(String workingDir,
                                        Set<String> inputDirectories,
                                        Set<String> resourcesDirectories,
                                        Either<String, String> outputDirOrJar,
                                        String mainClass,
                                        boolean generateJbManifest,
                                        String classpath,
                                        List<String> compilerArgs,
                                        IncrementalChanges incrementalChanges) {
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
        var sourceFiles = computeSourceFiles(inputDirectories, incrementalChanges);
        if (incrementalChanges == null && sourceFiles.isEmpty()) {
            var lookedAt = usingDefaultInputDirs
                    ? "src/main/java/, src/ and '.'"
                    : String.join(", ", inputDirectories);
            throw new JBuildException("No source files found " +
                    "(directories tried: " + lookedAt + ")", USER_INPUT);
        }

        var resourceFiles = computeResourceFiles(inputDirectories, resourcesDirectories, incrementalChanges);

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

        var outputDir = outputDirOrJar.map(
                outDir -> relativize(workingDir, outDir),
                jar -> getTempDirectory());

        log.verbosePrintln(() -> "Compilation output will be sent to " + outputDir);

        var jarFile = outputDirOrJar.map(NoOp.fun(), jar -> jarOrDefault(workingDir, jar));

        var deletionsDone = false;
        if (incrementalChanges != null && !incrementalChanges.deletedFiles.isEmpty()) {
            var deletions = computeDeletedFiles(
                    inputDirectories, resourcesDirectories, incrementalChanges.deletedFiles);
            if (!deletions.isEmpty()) {
                if (jarFile == null) {
                    deleteFilesFromDir(deletions, outputDir);
                } else {
                    deleteFilesFromJar(deletions, jarFile);
                }
                deletionsDone = true;
            }
        }

        if (sourceFiles.isEmpty() && resourceFiles.isEmpty()) {
            if (!deletionsDone) {
                log.println("No sources to compile. No resource files found. Nothing to do!");
            }
            return new CompileCommandResult(null, null);
        }

        if (!ensureDirectoryExists(new File(outputDir))) {
            throw new JBuildException(outputDir + " directory could not be created", IO_WRITE);
        }

        copyResources(resourceFiles, outputDir);

        ToolRunResult compileResult;
        if (sourceFiles.isEmpty()) {
            log.verbosePrintln("No source files to compile");
            compileResult = null;
        } else {
            compileResult = Tools.Javac.create().compile(sourceFiles, outputDir,
                    computeClasspath(relativize(workingDir, classpath), compilerArgs,
                            incrementalChanges == null ? null : jarFile == null ? outputDir : jarFile),
                    compilerArgs);
            if (compileResult.exitCode() != 0) {
                return new CompileCommandResult(compileResult, null);
            }
            log.verbosePrintln(() -> "Compilation successful on directory: " + outputDir);
        }

        if (generateJbManifest) {
            log.verbosePrintln("Generating jb manifest file");
            var generator = new JbManifestGenerator(log);
            var jbManifest = generator.generateJbManifest(outputDir);
            copyResources(List.of(jbManifest), outputDir);
        }

        if (jarFile == null) {
            return new CompileCommandResult(compileResult, null);
        }

        return new CompileCommandResult(compileResult,
                jar(mainClass, outputDir, jarFile, incrementalChanges));
    }

    private ToolRunResult jar(String mainClass,
                              String outputDir,
                              String jarFile,
                              IncrementalChanges incrementalChanges) {
        var jarContent = new FileSet(Set.of(), outputDir);

        if (incrementalChanges == null) {
            log.verbosePrintln(() -> "Creating jar file at " + jarFile);
            return Tools.Jar.create().createJar(new CreateJarOptions(
                    jarFile, mainClass, false, "", jarContent, Map.of()));
        }

        log.verbosePrintln(() -> "Updating jar file at " + jarFile);
        long startTime = System.currentTimeMillis();
        var result = Tools.Jar.create().updateJar(jarFile, jarContent);
        if (log.isVerbose()) {
            log.verbosePrintln("Updated jar in " + (System.currentTimeMillis() - startTime) + " ms");
        }
        return result;
    }

    private Set<String> computeSourceFiles(Set<String> inputDirectories,
                                           IncrementalChanges incrementalChanges) {
        if (incrementalChanges != null) {
            var sourceFiles = incrementalChanges.addedFiles.stream()
                    .filter(p -> p.endsWith(".java"))
                    .collect(toSet());
            if (!sourceFiles.isEmpty()) {
                log.verbosePrintln(() -> "Compiling " + incrementalChanges.addedFiles.size()
                        + " files added or modified since last compilation");
            }
            return sourceFiles;
        }
        return collectFiles(inputDirectories, JAVA_FILES_FILTER).stream()
                .flatMap(col -> col.files.stream())
                .collect(toSet());
    }

    private Set<String> computeDeletedFiles(Set<String> inputDirectories,
                                            Set<String> resourcesDirectories,
                                            Set<String> deletedFiles) {
        var directories = new HashSet<String>(inputDirectories.size() + resourcesDirectories.size());
        for (String resourcesDirectory : inputDirectories) {
            directories.add(ensureEndsWith(resourcesDirectory, File.separatorChar));
        }
        for (String resourcesDirectory : resourcesDirectories) {
            directories.add(ensureEndsWith(resourcesDirectory, File.separatorChar));
        }
        var result = new HashSet<String>(deletedFiles.size());
        for (String file : deletedFiles) {
            if (file.endsWith(".class")) {
                // class files must be passed with the exact output path, unlike resources
                result.add(file);
            } else {
                // resource files need to be relativized
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
            log.verbosePrintln(() -> "Deleting " + result.size() + " resource file" +
                    (result.size() == 1 ? "" : "s") + " from output");
        }
        return result;
    }

    private List<FileCollection> computeResourceFiles(Set<String> inputDirectories,
                                                      Set<String> resourcesDirectories,
                                                      IncrementalChanges incrementalChanges) {
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
                collectFiles(inputDirectories, NON_JAVA_FILES_FILTER),
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
            throw new JBuildException("Could not path existing jar file '" + jarFile + "' due to: " + e, IO_WRITE);
        }
        if (log.isVerbose()) {
            log.verbosePrintln("Deleted files from jar in " + (System.currentTimeMillis() - startTime) + "ms");
        }
    }

    private void copyResources(List<FileCollection> resourceFiles, String outputDir) {
        if (resourceFiles.isEmpty()) return;
        var outPath = Paths.get(outputDir);
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
                }
            }
        } catch (IOException e) {
            throw new JBuildException("Error copying resources: " + e, IO_WRITE);
        }
    }

    private static String computeClasspath(String classpath,
                                           List<String> compilerArgs,
                                           String previousOutput) {
        if (compilerArgs.contains("-cp")
                || compilerArgs.contains("--class-path")
                || compilerArgs.contains("--classpath")) {
            // explicit classpath was provided, use that only
            return "";
        }

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
        var cp = new File(classpath);
        if (cp.isDirectory()) {
            // expand classpath to include any jars available in the directory
            var jars = FileUtils.allFilesInDir(cp, JarFileFilter.getInstance());
            if (jars.length > 0) {
                return Stream.concat(Stream.of(classpath), Stream.of(jars).map(File::getPath))
                        .collect(joining(File.pathSeparator));
            }
        }
        return classpath;
    }

    private String jarOrDefault(String workingDir, String jar) {
        if (jar.isBlank()) {
            if (DEFAULT_JAR_FROM_WORKING_DIR.matcher(workingDir).find()) {
                return relativize(workingDir, new File(workingDir).getName() + ".jar");
            }
            var dir = System.getProperty("user.dir");
            if (dir != null) {
                var path = new File(dir).getName() + ".jar";
                log.verbosePrintln(() -> "Using default jar name based on working dir: " + path);
                return relativize(workingDir, path);
            } else {
                log.verbosePrintln("Using default jar name: lib.jar");
                return "lib.jar";
            }
        }
        return jar;
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

        public CompileCommandResult(ToolRunResult compileResult, ToolRunResult jarResult) {
            this.compileResult = compileResult;
            this.jarResult = jarResult;
        }

        public boolean isSuccessful() {
            return (compileResult == null || compileResult.exitCode() == 0)
                    && (jarResult == null || jarResult.exitCode() == 0);
        }

        public Optional<ToolRunResult> getCompileResult() {
            return Optional.ofNullable(compileResult);
        }

        public Optional<ToolRunResult> getJarResult() {
            return Optional.ofNullable(jarResult);
        }
    }

}
