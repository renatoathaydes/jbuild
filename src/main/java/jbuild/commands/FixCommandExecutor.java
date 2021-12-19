package jbuild.commands;

import jbuild.errors.JBuildException;
import jbuild.log.JBuildLog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.List;
import java.util.spi.ToolProvider;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;

public final class FixCommandExecutor {

    private final JBuildLog log;
    private final ToolProvider javap;
    private final ToolProvider jar;

    public FixCommandExecutor(JBuildLog log) {
        this.log = log;
        this.javap = lookupTool("javap");
        this.jar = lookupTool("jar");
    }

    private ToolProvider lookupTool(String tool) {
        return ToolProvider.findFirst(tool)
                .orElseThrow(() -> new JBuildException(tool + " is not available in this JVM, cannot run fix command.\n" +
                        "Consider using a full JDK installation to run jbuild.", ACTION_ERROR));
    }

    public void run(String inputDir, boolean interactive) {
        var dir = new File(inputDir);
        if (!dir.isDirectory()) {
            throw new JBuildException("not a directory: " + inputDir, USER_INPUT);
        }

        var files = dir.listFiles(name -> name.getName().endsWith(".jar"));

        if (files == null || files.length == 0) {
            log.println("No jar files found at " + inputDir + ", nothing to do.");
            return;
        }

        for (var file : files) {
            var classes = getClassesIn(file);
            for (var clazz : classes) {
                processClass(file, clazz);
            }
        }
    }

    private List<String> getClassesIn(File jarFile) {
        var out = new ByteArrayOutputStream(4096);
        var err = new ByteArrayOutputStream(4096);

        var code = jar.run(new PrintStream(out), new PrintStream(err), "tf", jarFile.getAbsolutePath());
        checkToolSuccessful("jar", code, err);

        return out.toString(UTF_8).lines()
                .filter(line -> line.endsWith(".class"))
                .filter(line -> !line.equals("module-info.class"))
                .map(line -> line.replace(File.separatorChar, '.')
                        .substring(0, line.length() - ".class".length()))
                .collect(toList());
    }

    private void processClass(File jar, String className) {
        var out = new ByteArrayOutputStream(4096);
        var err = new ByteArrayOutputStream(4096);

        int code = javap.run(new PrintStream(out), new PrintStream(err), "-c", "-classpath", jar.getAbsolutePath(), className);
        checkToolSuccessful("javap", code, err);

        processJavapOutput(className, out.toString(UTF_8).lines().collect(toList()));
    }

    private void processJavapOutput(String className, List<String> lines) {
        // TODO parse javap output to collect class dependencies on other classes
        if (lines.isEmpty()) {
            log.println("EMPTY " + className);
        } else {
            log.println(className + ": " + lines.get(0));
        }
    }

    private void checkToolSuccessful(String tool, int exitCode, ByteArrayOutputStream err) {
        if (exitCode != 0) {
            log.println("ERROR: " + tool + " exited with code " + exitCode);
            throw new JBuildException("unexpected error when executing " + tool +
                    ". Tool output:\n" + err.toString(UTF_8), ACTION_ERROR);
        }
    }
}
