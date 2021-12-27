package jbuild.commands;

import jbuild.errors.JBuildException;
import jbuild.java.ClassGraph;
import jbuild.java.CodeReference;
import jbuild.java.JavapOutputParser;
import jbuild.java.Tools;
import jbuild.java.code.Code;
import jbuild.java.code.TypeDefinition;
import jbuild.log.JBuildLog;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

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
        var classesByJar = parseClassDefinitionsInJars(inputDir);
        showInconsistencies(classesByJar);
    }

    public ClassGraph parseClassDefinitionsInJars(String inputDir) {
        var dir = new File(inputDir);
        if (!dir.isDirectory()) {
            throw new JBuildException("not a directory: " + inputDir, USER_INPUT);
        }

        var jarFiles = dir.listFiles(name -> name.getName().endsWith(".jar"));

        if (jarFiles == null || jarFiles.length == 0) {
            log.println("No jar files found at " + inputDir + ", nothing to do.");
            return new ClassGraph(Map.of());
        }

        return parseClassDefinitionsInJars(jarFiles);
    }

    public ClassGraph parseClassDefinitionsInJars(File... jarFiles) {

        var classesByJar = new HashMap<String, Map<String, TypeDefinition>>(jarFiles.length);
        for (var jar : jarFiles) {
            var classes = getClassesIn(jar);
            classesByJar.put(jar.getPath(), processClasses(jar, classes));
        }
        return new ClassGraph(classesByJar);
    }

    private void showInconsistencies(ClassGraph classGraph) {
        classGraph.getJarsByType().entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .forEach(entry -> {
                    var type = entry.getKey();
//                    log.println("Found " + type + " in jars: " + entry.getValue());
                    log.println("All references to type: " + type);
                    var code = new Code.Type(type);
                    var refs = classGraph.referencesTo(code);
                    for (CodeReference ref : refs) {
                        log.println("  * from " + ref.jar + "!" + ref.type + "::" + ref.getDefinition()
                                .map(m -> m.name + m.type)
                                .orElse("?"));
                    }
                });
    }

    private String[] getClassesIn(File jarFile) {
        var result = jar.listContents(jarFile.getAbsolutePath());
        checkToolSuccessful("jar", result);

        return result.stdout.lines()
                .filter(line -> line.endsWith(".class"))
                .filter(line -> !line.equals("module-info.class"))
                .map(line -> line.replace(File.separatorChar, '.')
                        .substring(0, line.length() - ".class".length()))
                .collect(toList())
                .toArray(String[]::new);
    }

    private Map<String, TypeDefinition> processClasses(File jar, String... classNames) {
        var result = javap.run(jar.getAbsolutePath(), classNames);
        checkToolSuccessful("javap", result);
        var javapOutputParser = new JavapOutputParser(log);
        return javapOutputParser.processJavapOutput(result.stdout.lines().iterator());
    }

    private void checkToolSuccessful(String tool, Tools.ToolRunResult result) {
        if (result.exitCode != 0) {
            log.println("ERROR: " + tool + " exited with code " + result.exitCode);
            throw new JBuildException("unexpected error when executing " + tool +
                    ". Tool output:\n" + result.stderr, ACTION_ERROR);
        }
    }

}
