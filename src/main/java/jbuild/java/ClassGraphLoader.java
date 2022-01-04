package jbuild.java;

import jbuild.java.code.TypeDefinition;
import jbuild.log.JBuildLog;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static jbuild.java.Tools.verifyToolSuccessful;
import static jbuild.util.AsyncUtils.awaitValues;

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

    public List<Supplier<CompletionStage<ClassGraph>>> fromJars(File... jarFiles) {
        var jarsByType = new HashMap<String, Set<String>>(128);

        for (var jar : jarFiles) {
            var classes = getClassesIn(jar);
            var jarPath = jar.getPath();
            for (String aClass : classes) {
                jarsByType.computeIfAbsent(aClass,
                        (ignore) -> new HashSet<>(2)
                ).add(jarPath);
            }
        }

        var jarSets = new JarSet.JarSetLoader(log)
                .computeUniqueJarSetPermutations(jarsByType);

        log.verbosePrintln(() -> "Found " + jarSets.size() + " different classpath permutations to check");

        return jarSets.stream()
                .map(this::lazyLoad)
                .collect(toList());
    }

    private Supplier<CompletionStage<ClassGraph>> lazyLoad(JarSet jarSet) {
        var cache = new ConcurrentHashMap<String, CompletionStage<Map<String, TypeDefinition>>>();
        return () -> {
            var typeDefsByJar = jarSet.getTypesByJar().entrySet().stream()
                    .collect(toMap(
                            Map.Entry::getKey,
                            entry -> cache.computeIfAbsent(entry.getKey(), (ignore) ->
                                    processClasses(new File(entry.getKey()), entry.getValue()))));
            return awaitValues(typeDefsByJar, t -> new ClassGraph(t, jarSet.getJarByType()));
        };
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

    private CompletionStage<Map<String, TypeDefinition>> processClasses(File jar, Collection<String> classNames) {
        return CompletableFuture.supplyAsync(() -> {
            var result = javap.run(jar.getAbsolutePath(), classNames.toArray(String[]::new));
            verifyToolSuccessful("javap", result);
            var javapOutputParser = new JavapOutputParser(log);
            return javapOutputParser.processJavapOutput(result.stdout.lines().iterator());
        });
    }

}
