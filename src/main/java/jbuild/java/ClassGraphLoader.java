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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static jbuild.java.Tools.verifyToolSuccessful;
import static jbuild.util.AsyncUtils.awaitValues;

public class ClassGraphLoader {

    private final JBuildLog log;

    public ClassGraphLoader(JBuildLog log) {
        this.log = log;
    }

    public static ClassGraphLoader create(JBuildLog log) {
        return new ClassGraphLoader(log);
    }

    public List<ClassGraphCompletion> fromJars(File... jarFiles) {
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

        var jarSets = new JarSet.Loader(log)
                .computeUniqueJarSetPermutations(jarsByType);

        log.verbosePrintln(() -> "Found " + jarSets.size() + " different classpath permutations to check");

        return jarSets.stream()
                .map(this::lazyLoad)
                .collect(toList());
    }

    private ClassGraphCompletion lazyLoad(JarSet jarSet) {
        var cacheByJar = new ConcurrentHashMap<String, CompletionStage<Map<String, TypeDefinition>>>();
        return new ClassGraphCompletion(jarSet, () -> {
            var startTime = System.currentTimeMillis();
            var completionsByJar = jarSet.getTypesByJar().entrySet().stream()
                    .collect(toMap(
                            Map.Entry::getKey,
                            entry -> cacheByJar.computeIfAbsent(entry.getKey(), (jar) ->
                                    processClasses(new File(jar), entry.getValue()))));
            return awaitValues(completionsByJar, typeDefsByJar -> {
                var jarByType = new HashMap<String, String>();
                typeDefsByJar.forEach((jar, types) -> {
                    for (var typeName : types.keySet()) {
                        jarByType.put(typeName, jar);
                    }
                });
                log.verbosePrintln(() -> "Created class graph for classpath in " +
                        (System.currentTimeMillis() - startTime) + " ms");
                return new ClassGraph(typeDefsByJar, jarByType);
            });
        });
    }

    private String[] getClassesIn(File jarFile) {
        var result = Tools.Jar.create().listContents(jarFile.getAbsolutePath());
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
        return supplyAsync(() -> {
            var result = Tools.Javap.create().run(jar.getAbsolutePath(), classNames.toArray(String[]::new));
            verifyToolSuccessful("javap", result);
            var javapOutputParser = new JavapOutputParser(log);
            return javapOutputParser.processJavapOutput(result.stdout.lines().iterator());
        });
    }

    public static final class ClassGraphCompletion {

        public final JarSet jarset;
        private final Supplier<CompletionStage<ClassGraph>> classGraphStage;

        public ClassGraphCompletion(JarSet jarset, Supplier<CompletionStage<ClassGraph>> classGraphStage) {
            this.jarset = jarset;
            this.classGraphStage = classGraphStage;
        }

        public CompletionStage<ClassGraph> getCompletion() {
            return classGraphStage.get();
        }
    }

}
