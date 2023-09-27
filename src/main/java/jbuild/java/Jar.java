package jbuild.java;

import jbuild.api.JBuildException;
import jbuild.classes.JBuildClassFileParser;
import jbuild.classes.model.ClassFile;
import jbuild.java.code.TypeDefinition;
import jbuild.java.tools.Tools;
import jbuild.log.JBuildLog;
import jbuild.util.CachedSupplier;
import jbuild.util.JavaTypeUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.stream.Collectors.toSet;
import static jbuild.api.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.api.JBuildException.ErrorCause.TIMEOUT;
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

    public List<ClassFile> parseAllTypes() {
        var parser = new JBuildClassFileParser();
        try (var zip = new ZipFile(file)) {
            return zip.stream().filter(s -> !s.isDirectory() && s.getName().endsWith(".class")).map(stream -> {
                try {
                    return parser.parse(zip.getInputStream(stream));
                } catch (IOException e) {
                    throw new JBuildException("Error reading jar: " + file + ": " + e,
                                              JBuildException.ErrorCause.IO_READ);
                } catch (Exception e) {
                    throw new JBuildException("Error parsing " + stream.getName() +
                                              " in jar: " + file + ": " + e,
                                              JBuildException.ErrorCause.ACTION_ERROR);      
                }
            }).collect(Collectors.toList());
        } catch (IOException e) {
            throw new JBuildException("Error reading jar: " + file + ": " + e,
                                      JBuildException.ErrorCause.IO_READ);
        }
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
    public static final class Loader implements AutoCloseable {

        private final JBuildLog log;
        private final ExecutorService loaderExecutorService;
        private final ExecutorService parserExecutorService;

        public Loader(JBuildLog log,
                      ExecutorService loaderExecutorService,
                      ExecutorService parserExecutorService) {
            this.log = log;
            this.loaderExecutorService = loaderExecutorService;
            this.parserExecutorService = parserExecutorService;
        }

        public Loader(JBuildLog log) {
            this(log, createExecutor(), createExecutor());
        }

        @Override
        public void close() {
            loaderExecutorService.shutdown();
            parserExecutorService.shutdown();
        }

        public static ExecutorService createExecutor() {
            return Executors.newFixedThreadPool(
                    Math.max(4, Runtime.getRuntime().availableProcessors()),
                    new JarLoaderThreadFactory());
        }

        public CompletionStage<Jar> lazyLoad(File jarFile) {
            return jarClassesIn(jarFile).thenApplyAsync(classes ->
                    lazyLoad(jarFile, classes), loaderExecutorService);
        }

        private Jar lazyLoad(File jar, Set<String> classNames) {
            var typeNames = classNames.stream()
                    .map(JavaTypeUtils::classNameToTypeName)
                    .collect(toSet());

            // the load method will be called only once, but lazily...
            // the Jar constructor caches the supplier.
            return new Jar(jar, typeNames, () -> supplyAsync(
                    () -> load(jar, classNames),
                    loaderExecutorService));
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
            }, loaderExecutorService);
        }

        private ParsedJar load(File jar, Set<String> classNames) {
            var startTime = System.currentTimeMillis();
            log.verbosePrintln(() -> "Parsing jar " + jar.getAbsolutePath() + " with " + classNames.size() + " classes");

            var partitions = partitionClasses(classNames);
            var totalTime = new AtomicLong(System.currentTimeMillis() - startTime);
            log.verbosePrintln(() -> "Partitioned jar types into " + partitions.size() +
                    " partitions in " + totalTime.get() + "ms");

            startTime = System.currentTimeMillis();
            final var typeDefs = new HashMap<String, TypeDefinition>(classNames.size());
            CompletableFuture<?>[] partitionFutures = new CompletableFuture[partitions.size()];

            for (var i = 0; i < partitions.size(); i++) {
                final var partitionIndex = i;
                partitionFutures[partitionIndex] = CompletableFuture.runAsync(() -> {
                    var partionTypes = parse(jar, partitions.get(partitionIndex), partitionIndex);
                    synchronized (typeDefs) {
                        typeDefs.putAll(partionTypes);
                    }
                }, parserExecutorService);
            }

            try {
                CompletableFuture.allOf(partitionFutures).get(5, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                throw new JBuildException("Interrupted while parsing bytecode in " + jar.getName(), ACTION_ERROR);
            } catch (ExecutionException e) {
                throw new JBuildException("Unexpected error parsing bytecode in " +
                        jar.getName() + ": " + e.getCause(), ACTION_ERROR);
            } catch (TimeoutException e) {
                log.println("Timeout parsing bytecode in " + jar.getName() + ": " +
                        (e.getMessage() == null ? "took too long" : e.getMessage()));
                throw new JBuildException("Timeout while parsing bytecode in " + jar.getName(), TIMEOUT);
            }

            totalTime.set(System.currentTimeMillis() - startTime);
            log.verbosePrintln(() -> "Finished processing all " + partitions.size() +
                    " partitions in " + totalTime.get() + "ms");

            return new ParsedJar(jar, typeDefs);
        }

        private static List<? extends Collection<String>> partitionClasses(Set<String> classNames) {
            final int partionSize = 250;
            List<Collection<String>> partitions;
            if (classNames.size() > partionSize + 100) {
                var classesIterator = classNames.iterator();
                int partitionCount = classNames.size() / partionSize;
                partitions = new ArrayList<>(partitionCount + 1);
                for (int i = 0; i < partitionCount; i++) {
                    var partition = new ArrayList<String>(partionSize);
                    partitions.add(partition);
                    for (int j = 0; j < partionSize; j++) {
                        partition.add(classesIterator.next());
                    }
                }
                // last partition
                {
                    var partition = new ArrayList<String>(classNames.size() % partionSize);
                    partitions.add(partition);
                    while (classesIterator.hasNext()) {
                        partition.add(classesIterator.next());
                    }
                }
            } else {
                partitions = List.of(classNames);
            }
            return partitions;
        }

        private Map<String, TypeDefinition> parse(File jar, Collection<String> classNames, int partitionIndex) {
            var startTime = System.currentTimeMillis();
            var javap = Tools.Javap.create();
            var toolResult = javap.run(jar.getAbsolutePath(), classNames);
            var totalTime = new AtomicLong(System.currentTimeMillis() - startTime);
            log.verbosePrintln(() -> "javap " + jar + " (partition " + partitionIndex +
                    ") completed in " + totalTime.get() + "ms");
            verifyToolSuccessful("javap", toolResult);
            startTime = System.currentTimeMillis();
            var javapOutputParser = new JavapOutputParser(log);
            Map<String, TypeDefinition> typeDefs;
            try (var stdoutStream = toolResult.getStdoutLines();
                 var ignored = toolResult.getStderrLines()) {
                typeDefs = javapOutputParser.processJavapOutput(stdoutStream.iterator());
            } catch (JBuildException e) {
                throw new JBuildException(e.getMessage() + " (jar: " + jar + ")", e.getErrorCause());
            }
            totalTime.set(System.currentTimeMillis() - startTime);
            log.verbosePrintln(() -> "JavapOutputParser parsed output for " + jar +
                    " (partition " + partitionIndex + ") in " + totalTime.get() + "ms");
            return typeDefs;
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
