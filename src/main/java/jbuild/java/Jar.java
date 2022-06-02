package jbuild.java;

import jbuild.errors.JBuildException;
import jbuild.java.code.TypeDefinition;
import jbuild.java.tools.Tools;
import jbuild.log.JBuildLog;
import jbuild.util.CachedSupplier;
import jbuild.util.JavaTypeUtils;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.stream.Collectors.toSet;
import static jbuild.java.tools.Tools.verifyToolSuccessful;

/**
 * A parsed jar file.
 * <p>
 * It contains information about a jar, including its types, and can lazily load its
 * {@link jbuild.java.code.TypeDefinition}s via the {@link Jar#parsed()} method.
 */
public final class Jar {

    public final File file;
    public final Set<String> types;

    private final Supplier<CompletionStage<ParsedJar>> computeParsedJar;

    Jar(File file,
        Set<String> types,
        Supplier<CompletionStage<ParsedJar>> computeParsedJar) {
        this.file = file;
        this.types = types;
        this.computeParsedJar = new CachedSupplier<>(computeParsedJar);
    }

    /**
     * Parse and load the jar if necessary, or return the already loaded jar if possible.
     *
     * @return the parsed jar
     */
    public CompletionStage<ParsedJar> parsed() {
        return computeParsedJar.get();
    }

    public String getName() {
        return file.getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Jar jar = (Jar) o;

        return file.equals(jar.file);
    }

    @Override
    public int hashCode() {
        return file.hashCode();
    }

    @Override
    public String toString() {
        return "Jar{" +
                "file=" + file +
                ", typeCount=" + types.size() +
                '}';
    }

    /**
     * Parsed contents of a jar.
     */
    public static final class ParsedJar {
        public final File file;
        public final Map<String, TypeDefinition> typeByName;

        public ParsedJar(File file, Map<String, TypeDefinition> typeByName) {
            this.file = file;
            this.typeByName = typeByName;
        }

        public Set<String> getTypes() {
            return typeByName.keySet();
        }
    }

    /**
     * Asynchronous loader of {@link jbuild.java.tools.Tools.Jar} instances.
     */
    public static final class Loader {

        private final JBuildLog log;
        private final ExecutorService executorService;

        public Loader(JBuildLog log,
                      ExecutorService executorService) {
            this.log = log;
            this.executorService = executorService;
        }

        public Loader(JBuildLog log) {
            this(log, createExecutor());
        }

        public static ExecutorService createExecutor() {
            return Executors.newFixedThreadPool(
                    Math.max(4, Runtime.getRuntime().availableProcessors()),
                    new JarLoaderThreadFactory());
        }

        public CompletionStage<Jar> lazyLoad(File jarFile) {
            return jarClassesIn(jarFile).thenApplyAsync(classes ->
                    lazyLoad(jarFile, classes), executorService);
        }

        private Jar lazyLoad(File jar, Set<String> classNames) {
            var typeNames = classNames.stream()
                    .map(JavaTypeUtils::classNameToTypeName)
                    .collect(toSet());

            // the load method will be called only once, but lazily...
            // the Jar constructor caches the supplier.
            return new Jar(jar, typeNames, () -> supplyAsync(
                    () -> load(jar, classNames),
                    executorService));
        }

        private CompletionStage<Set<String>> jarClassesIn(File jar) {
            return supplyAsync(() -> {
                var result = Tools.Jar.create().listContents(jar.getPath());
                verifyToolSuccessful("jar", result);
                return result.getStdout().lines()
                        .filter(line -> line.endsWith(".class") &&
                                !line.endsWith("-info.class"))
                        .map(line -> line.replace('/', '.')
                                .substring(0, line.length() - ".class".length()))
                        .collect(toSet());
            }, executorService);
        }

        private ParsedJar load(File jar, Set<String> classNames) {
            var startTime = System.currentTimeMillis();
            log.verbosePrintln(() -> "Parsing jar " + jar.getAbsolutePath() + " with " + classNames.size() + " classes");
            var javap = classNames.size() > 4_000 // can run out of memory when parsing so many classes
                    ? Tools.Javap.createFileBacked()
                    : Tools.Javap.create();
            var toolResult = javap.run(jar.getAbsolutePath(), classNames);
            var totalTime = new AtomicLong(System.currentTimeMillis() - startTime);
            log.verbosePrintln(() -> "javap " + jar + " completed in " + totalTime.get() + "ms");
            verifyToolSuccessful("javap", toolResult);
            var javapOutputParser = new JavapOutputParser(log);
            startTime = System.currentTimeMillis();
            Map<String, TypeDefinition> typeDefs;
            try (var stdoutStream = toolResult.getStdoutLines();
                 var ignored = toolResult.getStderrLines()) {
                typeDefs = javapOutputParser.processJavapOutput(stdoutStream.iterator());
            } catch (JBuildException e) {
                throw new JBuildException(e.getMessage() + " (jar: " + jar + ")", e.getErrorCause());
            }
            totalTime.set(System.currentTimeMillis() - startTime);
            log.verbosePrintln(() -> "JavapOutputParser parsed output for " + jar + " in " + totalTime.get() + "ms");
            return new ParsedJar(jar, typeDefs);
        }

    }

    private static final class JarLoaderThreadFactory implements ThreadFactory {
        private final AtomicInteger count = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable runnable) {
            var thread = new Thread(runnable, "jar-loader-" + count.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
