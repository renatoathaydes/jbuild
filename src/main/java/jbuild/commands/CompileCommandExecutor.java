package jbuild.commands;

import jbuild.errors.JBuildException;
import jbuild.java.tools.CreateJarOptions;
import jbuild.java.tools.CreateJarOptions.FileSet;
import jbuild.java.tools.ToolRunResult;
import jbuild.java.tools.Tools;
import jbuild.log.JBuildLog;
import jbuild.util.Either;
import jbuild.util.FileUtils;
import jbuild.util.JarFileFilter;
import jbuild.util.NoOp;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;

public final class CompileCommandExecutor {

    private final JBuildLog log;

    public CompileCommandExecutor(JBuildLog log) {
        this.log = log;
    }

    public CompileCommandResult compile(Set<String> inputDirectories,
                                        Either<String, String> outputDirOrJar,
                                        String mainClass,
                                        String classpath,
                                        List<String> compilerArgs) {
        var usingDefaultInputDirs = inputDirectories.isEmpty();
        if (usingDefaultInputDirs) {
            inputDirectories = computeDefaultSourceDirs();
        }
        var files = collectSourceFiles(inputDirectories).collect(toSet());
        if (files.isEmpty()) {
            var lookedAt = usingDefaultInputDirs
                    ? "src/main/java/, src/ and '.'"
                    : String.join(", ", inputDirectories);
            throw new JBuildException("No source files found " +
                    "(directories tried: " + lookedAt + ")", USER_INPUT);
        }

        log.verbosePrintln(() -> "Found " + files.size() + " source file(s) to compile");

        var outputDir = outputDirOrJar.map(
                outDir -> outDir,
                jar -> getTempDirectory());
        var jarFile = outputDirOrJar.map(NoOp.fun(), this::jarOrDefault);

        var compileResult = Tools.Javac.create().compile(files, outputDir,
                computeClasspath(classpath, compilerArgs), compilerArgs);
        if (jarFile == null || compileResult.exitCode() != 0) {
            return new CompileCommandResult(compileResult, null);
        }

        log.verbosePrintln(() -> "Compilation of class files successful. Creating jar at " + jarFile);
        var jarContent = new FileSet(Set.of(), outputDir);
        var jarResult = Tools.Jar.create().createJar(new CreateJarOptions(
                jarFile, mainClass, false, "", jarContent, Map.of()
        ));
        return new CompileCommandResult(compileResult, jarResult);
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

    private Stream<String> collectSourceFiles(Set<String> inputDirectories) {
        return inputDirectories.stream()
                .flatMap(dirPath -> collectFiles(dirPath, (dir, name) -> name.endsWith(".java")));
    }

    private Stream<String> collectFiles(String dirPath,
                                        FilenameFilter filter) {
        var dir = new File(dirPath);
        if (!dir.isDirectory()) {
            log.println(() -> "Ignoring non-existing input directory: " + dirPath);
            return Stream.of();
        }
        var children = dir.listFiles();
        if (children != null) {
            return Stream.of(children)
                    .flatMap(child -> fileOrChildDirectories(child, filter));
        }
        return Stream.of();
    }

    private Stream<String> fileOrChildDirectories(File file, FilenameFilter filter) {
        if (file.isFile() && filter.accept(file.getParentFile(), file.getName())) {
            return Stream.of(file.getPath());
        }
        if (file.isDirectory()) {
            var children = file.listFiles();
            if (children != null) {
                return Stream.of(children)
                        .flatMap(child -> fileOrChildDirectories(child, filter));
            }
        }
        return Stream.of();
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
