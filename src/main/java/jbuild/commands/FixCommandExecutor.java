package jbuild.commands;

import jbuild.errors.JBuildException;
import jbuild.java.JavapOutputParser;
import jbuild.java.Tools;
import jbuild.java.code.Code;
import jbuild.log.JBuildLog;

import java.io.File;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;

public final class FixCommandExecutor {

    private final JBuildLog log;
    private final Tools.Javap javap;
    private final Tools.Jar jar;

    public FixCommandExecutor(JBuildLog log) {
        this.log = log;
        this.javap = Tools.Javap.create();
        this.jar = Tools.Jar.create();
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
        var result = jar.listContents(jarFile.getAbsolutePath());
        checkToolSuccessful("jar", result);

        return result.stdout.lines()
                .filter(line -> line.endsWith(".class"))
                .filter(line -> !line.equals("module-info.class"))
                .map(line -> line.replace(File.separatorChar, '.')
                        .substring(0, line.length() - ".class".length()))
                .collect(toList());
    }

    private void processClass(File jar, String className) {
        var result = javap.run(jar.getAbsolutePath(), className);
        checkToolSuccessful("javap", result);

        var javapOutputParser = new JavapOutputParser(log);

        var classDef = javapOutputParser.processJavapOutput(className, result.stdout.lines().iterator());

        log.println("Class " + classDef.className + " has methods:");
        classDef.methods.forEach((method, c) -> {
            log.println("  - name=" + method.name + ", type=" + method.type);
            for (Code code1 : c) {
                log.println("      " + code1);
            }
        });
    }

    private void checkToolSuccessful(String tool, Tools.ToolRunResult result) {
        if (result.exitCode != 0) {
            log.println("ERROR: " + tool + " exited with code " + result.exitCode);
            throw new JBuildException("unexpected error when executing " + tool +
                    ". Tool output:\n" + result.stderr, ACTION_ERROR);
        }
    }

}
