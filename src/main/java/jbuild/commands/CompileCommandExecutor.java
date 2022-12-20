package jbuild.commands;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.errors.JBuildException.ErrorCause.IO_WRITE;
import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;
import static jbuild.util.CollectionUtils.appendAsStream;
import static jbuild.util.FileUtils.collectFiles;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import jbuild.errors.JBuildException;
import jbuild.java.tools.CreateJarOptions;
import jbuild.java.tools.CreateJarOptions.FileSet;
import jbuild.java.tools.ToolRunResult;
import jbuild.java.tools.Tools;
import jbuild.log.JBuildLog;
import jbuild.util.Either;
import jbuild.util.FileCollection;
import jbuild.util.FileUtils;
import jbuild.util.JarFileFilter;
import jbuild.util.NoOp;

public final class CompileCommandExecutor {

    private static final FilenameFilter JAVA_FILES_FILTER = (dir, name) -> name.endsWith(".java");
    private static final FilenameFilter NON_JAVA_FILES_FILTER = (dir, name) -> !name.endsWith(".java");
    private static final FilenameFilter ALL_FILES_FILTER = (dir, name) -> true;

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
            List<String> compilerArgs) {
        var usingDefaultInputDirs = inputDirectories.isEmpty();
        if (usingDefaultInputDirs) {
            inputDirectories = computeDefaultSourceDirs();
        }
        if (resourcesDirectories.isEmpty()) {
            resourcesDirectories = computeDefaultResourceDirs();
        }
        var sourceFiles = collectFiles(inputDirectories, JAVA_FILES_FILTER)
                .stream().flatMap(col -> col.files.stream())
                .collect(toSet());
        if (sourceFiles.isEmpty()) {
            var lookedAt = usingDefaultInputDirs
                    ? "src/main/java/, src/ and '.'"
                    : String.join(", ", inputDirectories);
            throw new JBuildException("No source files found " +
                    "(directories tried: " + lookedAt + ")", USER_INPUT);
        }

        var resourceFiles = appendAsStream(
                collectFiles(inputDirectories, NON_JAVA_FILES_FILTER),
                collectFiles(resourcesDirectories, ALL_FILES_FILTER)).collect(toList());

        if (log.isVerbose()) {
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
                outDir -> outDir,
                jar -> getTempDirectory());
        var jarFile = outputDirOrJar.map(NoOp.fun(), this::jarOrDefault);

        copyResources(resourceFiles, outputDir);

        var compileResult = Tools.Javac.create().compile(sourceFiles, outputDir,
                computeClasspath(classpath, compilerArgs), compilerArgs);
        if (jarFile == null || compileResult.exitCode() != 0) {
            return new CompileCommandResult(compileResult, null);
        }

        log.verbosePrintln(() -> "Compilation of class files successful. Creating jar at " + jarFile);
        if (generateJbManifest) {
            log.verbosePrintln("Generating jb manifest file");
            var generator = new JbManifestGenerator(log);
            var jbManifest = generator.generateJbManifest(outputDir);
            copyResources(List.of(jbManifest), outputDir);
        }

        var jarContent = new FileSet(Set.of(), outputDir);
        var jarResult = Tools.Jar.create().createJar(new CreateJarOptions(
                jarFile, mainClass, false, "", jarContent, Map.of()));
        return new CompileCommandResult(compileResult, jarResult);
    }

    private void copyResources(List<FileCollection> resourceFiles, String outputDir) {
        var out = new File(outputDir);
        if (!out.mkdirs() && !out.isDirectory()) {
            throw new JBuildException(outputDir + " directory could not be created", IO_WRITE);
        }
        var outPath = out.toPath();
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
            List<String> compilerArgs) {
        if (compilerArgs.contains("-cp")
                || compilerArgs.contains("--class-path")
                || compilerArgs.contains("--classpath")) {
            // explicit classpath was provided, use that only
            return "";
        }
        return Stream.of(classpath.split(File.pathSeparator))
                .map(CompileCommandExecutor::computeClasspathPart)
                .filter(not(String::isBlank))
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

    private String jarOrDefault(String jar) {
        if (jar.isBlank()) {
            var dir = System.getProperty("user.dir");
            if (dir != null) {
                var path = new File(dir).getName() + ".jar";
                log.verbosePrintln(() -> "Using default jar name based on working dir: " + path);
                return path;
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

    private Set<String> computeDefaultSourceDirs() {
        var srcMainJava = Paths.get("src", "main", "java");
        if (srcMainJava.toFile().isDirectory()) {
            log.verbosePrintln(() -> "Using source directory: " + srcMainJava);
            return Set.of(srcMainJava.toString());
        }
        var src = Paths.get("src");
        if (src.toFile().isDirectory()) {
            log.verbosePrintln(() -> "Using source directory: " + src);
            return Set.of(src.toString());
        }
        log.verbosePrintln(() -> "Using working directory as source directory: " + Paths.get(".").toAbsolutePath());
        return Set.of(".");
    }

    private Set<String> computeDefaultResourceDirs() {
        var srcMainResources = Paths.get("src", "main", "resources");
        if (srcMainResources.toFile().isDirectory()) {
            log.verbosePrintln(() -> "Using resource directory: " + srcMainResources);
            return Set.of(srcMainResources.toString());
        }
        var res = Paths.get("resources");
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
            return compileResult.exitCode() == 0 && (jarResult != null && jarResult.exitCode() == 0);
        }

        public ToolRunResult getCompileResult() {
            return compileResult;
        }

        public Optional<ToolRunResult> getJarResult() {
            return Optional.ofNullable(jarResult);
        }
    }

}
