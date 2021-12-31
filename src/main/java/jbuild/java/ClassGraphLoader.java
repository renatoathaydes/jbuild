package jbuild.java;

import jbuild.errors.JBuildException;
import jbuild.java.code.TypeDefinition;
import jbuild.log.JBuildLog;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;
import static jbuild.java.Tools.verifyToolSuccessful;

public class ClassGraphLoader {

    private final Tools.Jar jar;
    private final Tools.Javap javap;
    private final JBuildLog log;

    public ClassGraphLoader(Tools.Jar jar, Tools.Javap javap, JBuildLog log) {
        this.jar = jar;
        this.javap = javap;
        this.log = log;
    }

    public static ClassGraphLoader create(JBuildLog log) {
        return new ClassGraphLoader(Tools.Jar.create(), Tools.Javap.create(), log);
    }

    public ClassGraph fromJars(File... jarFiles) {
        var classesByJar = new HashMap<String, Map<String, TypeDefinition>>(jarFiles.length);
        for (var jar : jarFiles) {
            var classes = getClassesIn(jar);
            classesByJar.put(jar.getPath(), processClasses(jar, classes));
        }
        return new ClassGraph(classesByJar);
    }

    public ClassGraph fromJarsInDirectory(String inputDir) {
        var dir = new File(inputDir);
        if (!dir.isDirectory()) {
            throw new JBuildException("not a directory: " + inputDir, USER_INPUT);
        }

        var jarFiles = dir.listFiles(name -> name.getName().endsWith(".jar"));

        if (jarFiles == null || jarFiles.length == 0) {
            log.println("No jar files found at " + inputDir + ", nothing to do.");
            return new ClassGraph(Map.of());
        }

        return fromJars(jarFiles);
    }

    private String[] getClassesIn(File jarFile) {
        var result = jar.listContents(jarFile.getAbsolutePath());
        verifyToolSuccessful("jar", result);

        return result.stdout.lines()
                .filter(line -> line.endsWith(".class") &&
                        !line.endsWith("-info.class"))
                .map(line -> line.replace(File.separatorChar, '.')
                        .substring(0, line.length() - ".class".length()))
                .collect(toList())
                .toArray(String[]::new);
    }

    private Map<String, TypeDefinition> processClasses(File jar, String... classNames) {
        var result = javap.run(jar.getAbsolutePath(), classNames);
        verifyToolSuccessful("javap", result);
        var javapOutputParser = new JavapOutputParser(log);
        return javapOutputParser.processJavapOutput(result.stdout.lines().iterator());
    }

}
