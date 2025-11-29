package jbuild.commands;

import jbuild.api.JBuildException;
import jbuild.api.JBuildException.ErrorCause;
import jbuild.classes.model.ClassFile;
import jbuild.classes.parser.JBuildClassFileParser;
import jbuild.java.Jar;
import jbuild.log.JBuildLog;
import jbuild.util.Either;
import jbuild.util.FileCollection;
import jbuild.util.FileUtils;
import jbuild.util.JavaTypeUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.supplyAsync;
import static jbuild.util.JavaTypeUtils.typeNameToClassName;

public class RequirementsCommandExecutor {

    private final JBuildLog log;
    private final TypeVisitor missingTypeVisitor;

    public RequirementsCommandExecutor(JBuildLog log, TypeVisitor visitor) {
        this.log = log;
        this.missingTypeVisitor = visitor;
    }

    private RequirementsCommandExecutor(JBuildLog log) {
        this.log = log;
        this.missingTypeVisitor = new DefaultTypeVisitor();
    }

    public static RequirementsCommandExecutor createDefault(JBuildLog log) {
        return new RequirementsCommandExecutor(log);
    }

    @SuppressWarnings("resource")
    public CompletionStage<Void> execute(Set<String> files, boolean perClass) {
        var jarLoader = new Jar.Loader(log);

        // there can be no interleaving visiting jars
        var reporterThread = Executors.newSingleThreadExecutor();

        var futures = files.stream()
                .map(file -> typesSource(file, jarLoader, reporterThread).thenCompose(types -> supplyAsync(() -> types.map(
                        fileCollection -> typesRequiredBy(fileCollection, perClass),
                        jar -> typesRequiredBy(jar, perClass)), reporterThread
                ).thenAcceptAsync(requiredTypes -> visit(file, requiredTypes, perClass), reporterThread)))
                .map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures).whenComplete((_1, _2) -> {
            reporterThread.shutdown();
            jarLoader.close();
        });
    }

    private CompletionStage<Either<FileCollection, Jar>> typesSource(
            String path,
            Jar.Loader jarLoader,
            ExecutorService executor) {
        var file = new File(path);
        if (file.isDirectory()) {
            return supplyAsync(() -> Either.left(FileUtils.collectFiles(file.getPath(), FileUtils.CLASS_FILES_FILTER)), executor);
        }
        // check if the path is a class file
        if (file.getName().endsWith(".class")) {
            var classFile = new FileCollection(file.getParent(), List.of(file.getPath()));
            return supplyAsync(() -> Either.left(classFile), executor);
        }
        // treat path as a jar
        return jarLoader.lazyLoad(file).thenApply(Either::right);
    }

    private void visit(String file, Map<String, TypeRequirements> requiredTypes, boolean perClass) {
        missingTypeVisitor.start(file);
        if (perClass) {
            requiredTypes.forEach(missingTypeVisitor::handleTypeRequirements);
        } else
            for (var types : requiredTypes.values()) {
                missingTypeVisitor.handleJarRequirements(types.requirements);
            }
        missingTypeVisitor.onDone();
    }

    private Map<String, TypeRequirements> typesRequiredBy(FileCollection fileCollection, boolean perClass) {
        var parser = new JBuildClassFileParser();
        var resultMap = new TreeMap<String, TypeRequirements>();
        var getSet = createClassFileCollector(resultMap, perClass);
        var types = fileCollection.files.stream()
                .map(JavaTypeUtils::fileToTypeName)
                .collect(Collectors.toSet());

        log.verbosePrintln(() -> "Collecting types required by " + types.size() + " class files at " + fileCollection.directory);

        var startTime = System.currentTimeMillis();

        for (var file : fileCollection.files) {
            try (var stream = new FileInputStream(file)) {
                var classFile = parser.parse(stream);
                collectRequirements(getSet, classFile, perClass, types);
            } catch (IOException e) {
                throw new JBuildException("Could not open file " + file + ": " + e, ErrorCause.IO_READ);
            } catch (Exception e) {
                throw new JBuildException("Error parsing " + file + ": " + e, ErrorCause.ACTION_ERROR);
            }
        }

        log.verbosePrintln(() -> "Collected " + resultMap.values().stream().mapToInt(t -> t.requirements.size()).sum() +
                " type requirements in " + (System.currentTimeMillis() - startTime) + "ms");

        return resultMap;
    }

    private Map<String, TypeRequirements> typesRequiredBy(Jar jar, boolean perClass) {
        var jarTypes = jar.types;
        var resultMap = new TreeMap<String, TypeRequirements>();
        var getSet = createClassFileCollector(resultMap, perClass);
        var classFiles = jar.parseAllTypes();

        log.verbosePrintln(() -> "Collecting types required by " + jar.file + "'s " + classFiles.size() + " class files");

        var startTime = System.currentTimeMillis();

        for (ClassFile file : classFiles) {
            collectRequirements(getSet, file, perClass, jarTypes);
        }

        log.verbosePrintln(() -> "Collected " + resultMap.values().stream().mapToInt(t -> t.requirements.size()).sum() +
                " type requirements in " + (System.currentTimeMillis() - startTime) + "ms");

        return resultMap;
    }

    private Function<ClassFile, TreeSet<String>> createClassFileCollector(TreeMap<String, TypeRequirements> resultMap,
                                                                          boolean perClass) {
        if (perClass) {
            return (classFile) -> {
                var set = new TreeSet<String>();
                resultMap.put(classFile.getTypeName(), new TypeRequirements(classFile, set));
                return set;
            };
        } else {
            var set = new TreeSet<String>();
            resultMap.put("", new TypeRequirements(null, set));
            return (ignore) -> set;
        }
    }

    private void collectRequirements(Function<ClassFile, TreeSet<String>> getSet,
                                     ClassFile file,
                                     boolean perClass,
                                     Set<String> jarTypes) {
        var requirements = getSet.apply(file);
        var parentTypeName = file.getTypeName();
        for (String typeName : file.getAllTypes()) {
            if (JavaTypeUtils.isPrimitiveJavaType(typeName) ||
                    JavaTypeUtils.mayBeJavaStdLibType(typeName) ||
                    typeName.equals(parentTypeName)) {
                continue;
            }
            if (perClass || !jarTypes.contains(typeName)) {
                requirements.add(typeName);
            }
        }
    }

    public static final class TypeRequirements {
        public final ClassFile classFile;
        public final TreeSet<String> requirements;

        public TypeRequirements(ClassFile classFile, TreeSet<String> requirements) {
            this.classFile = classFile;
            this.requirements = requirements;
        }
    }

    public interface TypeVisitor {
        void start(String path);

        void handleTypeRequirements(String type, TypeRequirements typeRequirements);

        void handleJarRequirements(TreeSet<String> types);

        void onDone();
    }

    private final class DefaultTypeVisitor implements TypeVisitor {

        private String path;
        private int count;
        private boolean perClass;

        @Override
        public void start(String path) {
            log.println("Required type(s) for " + path + ':');
            this.path = path;
            count = 0;
        }

        @Override
        public void handleTypeRequirements(String type, TypeRequirements typeRequirements) {
            perClass = true;
            log.println("  - " + typeNameToClassName(type) + " (" + typeRequirements.classFile.getSourceFile() + "):");
            for (var requirement : typeRequirements.requirements) {
                log.println("    * " + typeNameToClassName(requirement));
            }
        }

        @Override
        public void handleJarRequirements(TreeSet<String> types) {
            for (var type : types) {
                log.println("    * " + typeNameToClassName(type));
                count++;
            }
        }

        @Override
        public void onDone() {
            if (perClass) {
                log.println("");
                return;
            }
            if (count == 0) {
                log.println(() -> "  " + path + " has no type requirements.");
            } else {
                log.println(() -> "  total " + count + " type" + (count == 1 ? "" : "s") + " listed.");
            }
        }
    }
}
