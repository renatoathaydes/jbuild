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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

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
        var jarsByType = new HashMap<String, Set<File>>(128);
        var jarTool = Tools.Jar.create();
        for (var jar : jarFiles) {
            var classes = getClassesIn(jarTool, jar);
            for (var type : classes) {
                jarsByType.computeIfAbsent(type,
                        (ignore) -> new HashSet<>(2)
                ).add(jar);
            }
        }

        var jarSets = new JarSet.Loader(log)
                .computeUniqueJarSetPermutations(jarsByType);

        return jarSets.stream()
                .map(this::lazyLoad)
                .collect(toList());
    }

    private ClassGraphCompletion lazyLoad(JarSet jarSet) {
        var cacheByJar = new ConcurrentHashMap<File, CompletionStage<Map<String, TypeDefinition>>>();
        return new ClassGraphCompletion(jarSet, () -> {
            var startTime = System.currentTimeMillis();
            var completionsByJar = jarSet.getTypesByJar().entrySet().stream()
                    .collect(toMap(
                            Map.Entry::getKey,
                            entry -> cacheByJar.computeIfAbsent(entry.getKey(), (jar) ->
                                    processClasses(jar, entry.getValue()))));
            return awaitValues(completionsByJar, typeDefsByJar -> {
                var jarByType = new HashMap<String, File>();
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

    private String[] getClassesIn(Tools.Jar jar, File jarFile) {
        var result = jar.listContents(jarFile.getPath());
        verifyToolSuccessful("jar", result);

        return result.stdout.lines()
                .filter(line -> line.endsWith(".class") &&
                        !line.endsWith("-info.class"))
                .map(line -> line.replace('/', '.')
                        .substring(0, line.length() - ".class".length()))
                .collect(toList())
                .toArray(String[]::new);
    }

    private CompletionStage<Map<String, TypeDefinition>> processClasses(File jar, Collection<String> classNames) {
        var result = new CompletableFuture<Map<String, TypeDefinition>>();

        // this call can block for a long time, use a new Thread to avoid blocking concurrent work
        new Thread(() -> {
            var startTime = System.currentTimeMillis();
            try {
                var toolResult = Tools.Javap.create().run(jar.getAbsolutePath(), classNames.toArray(String[]::new));
                var totalTime = new AtomicLong(System.currentTimeMillis() - startTime);
                log.verbosePrintln(() -> "javap " + jar + " completed in " + totalTime.get() + "ms");
                verifyToolSuccessful("javap", toolResult);
                var javapOutputParser = new JavapOutputParser(log);
                startTime = System.currentTimeMillis();
                var typeDefs = javapOutputParser.processJavapOutput(toolResult.stdout.lines().iterator());
                totalTime.set(System.currentTimeMillis() - startTime);
                log.verbosePrintln(() -> "JavapOutputParser parsed output for " + jar + " in " + totalTime.get() + "ms");
                result.complete(typeDefs);
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        }).start();

        return result;
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
