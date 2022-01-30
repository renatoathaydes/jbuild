package jbuild.commands;

import jbuild.java.tools.ToolRunResult;
import jbuild.java.tools.Tools;
import jbuild.log.JBuildLog;

import java.io.File;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

public final class CompileCommandExecutor {

    private final JBuildLog log;

    public CompileCommandExecutor(JBuildLog log) {
        this.log = log;
    }

    public ToolRunResult compile(Set<String> inputDirectories,
                                 String outputDir,
                                 String classpath) {
        if (inputDirectories.isEmpty()) {
            inputDirectories = Set.of(".");
        }
        var files = collectSourceFiles(inputDirectories).collect(toSet());
        log.verbosePrintln(() -> "Found " + files.size() + " source file(s) to compile");
        return Tools.Javac.create().compile(files, outputDir, classpath);
    }

    private Stream<String> collectSourceFiles(Set<String> inputDirectories) {
        return inputDirectories.stream()
                .flatMap(dirPath -> {
                    var dir = new File(dirPath);
                    if (!dir.isDirectory()) {
                        log.println(() -> "Ignoring non-existing input directory: " + dirPath);
                        return Stream.of();
                    }
                    var children = dir.listFiles();
                    if (children != null) {
                        return Stream.of(children)
                                .flatMap(this::javaFileOrChildDirectories);
                    }
                    return Stream.of();
                });
    }

    private Stream<String> javaFileOrChildDirectories(File file) {
        if (file.isFile() && file.getName().endsWith(".java")) {
            return Stream.of(file.getPath());
        }
        if (file.isDirectory()) {
            var children = file.listFiles();
            if (children != null) {
                return Stream.of(children)
                        .flatMap(this::javaFileOrChildDirectories);
            }
        }
        return Stream.of();
    }

}
