package jbuild.commands;

import jbuild.errors.JBuildException;
import jbuild.java.tools.CreateJarOptions;
import jbuild.java.tools.CreateJarOptions.FileSet;
import jbuild.java.tools.ToolRunResult;
import jbuild.java.tools.Tools;
import jbuild.log.JBuildLog;
import jbuild.util.Either;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;

public final class CompileCommandExecutor {

    private final JBuildLog log;

    public CompileCommandExecutor(JBuildLog log) {
        this.log = log;
    }

    public ToolRunResult compile(Set<String> inputDirectories,
                                 Either<String, String> outputDirOrJar,
                                 String classpath) {
        if (inputDirectories.isEmpty()) {
            inputDirectories = Set.of(".");
        }
        var files = collectSourceFiles(inputDirectories).collect(toSet());
        log.verbosePrintln(() -> "Found " + files.size() + " source file(s) to compile");

        var outputDir = outputDirOrJar.map(
                outDir -> outDir,
                jar -> getTempDirectory());
        var jarFile = outputDirOrJar.map(outDir -> null, jar -> jar);

        var compileResult = Tools.Javac.create().compile(files, outputDir, classpath);
        if (jarFile == null || compileResult.exitCode() != 0) return compileResult;

        log.verbosePrintln(() -> "Compilation of class files successful. Creating jar at " + jarFile);
        var jarContent = new FileSet(Set.of(), outputDir);
        return Tools.Jar.create().createJar(new CreateJarOptions(
                jarFile, "", false, "", jarContent, Map.of()
        ));
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

}
