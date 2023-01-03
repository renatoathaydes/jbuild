package jbuild.commands;

import jbuild.classes.model.ClassFile;
import jbuild.java.Jar;
import jbuild.log.JBuildLog;
import jbuild.util.JavaTypeUtils;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.supplyAsync;
import static jbuild.util.JavaTypeUtils.typeNameToClassName;

public class RequirementsCommandExecutor {

    private final JBuildLog log;
    private final TypeVisitor missingTypeVisitor;

    public RequirementsCommandExecutor(
            JBuildLog log,
            TypeVisitor visitor) {
        this.log = log;
        this.missingTypeVisitor = visitor;
    }

    private RequirementsCommandExecutor(
            JBuildLog log) {
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

        var futures = files.stream().map(file ->
                        jarLoader.lazyLoad(new File(file)).thenCompose(
                                jar -> supplyAsync(() -> typesRequiredBy(jar, perClass), reporterThread)
                                        .thenAcceptAsync(requiredTypes -> visit(jar, requiredTypes, perClass), reporterThread))
                ).map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures).whenComplete((_1, _2) -> {
            reporterThread.shutdown();
            jarLoader.close();
        });
    }

    private void visit(Jar jarFile, Map<String, ? extends Set<String>> requiredTypes, boolean perClass) {
        missingTypeVisitor.startJar(jarFile.file);
        if (perClass) {
            requiredTypes.forEach((type, requirements) -> {
                missingTypeVisitor.startType(type);
                for (var requiredType : requirements) {
                    missingTypeVisitor.onRequiredType(requiredType);
                }
            });
        } else for (var types : requiredTypes.values()) {
            for (var requiredType : types) {
                missingTypeVisitor.onRequiredType(requiredType);
            }
        }
        missingTypeVisitor.onDone();
    }

    private Map<String, ? extends Set<String>> typesRequiredBy(Jar jar, boolean perClass) {
        var jarTypes = jar.types;
        var resultMap = new TreeMap<String, TreeSet<String>>();
        Function<String, TreeSet<String>> getSet;
        if (perClass) {
            getSet = (type) -> {
                var set = new TreeSet<String>();
                resultMap.put(type, set);
                return set;
            };
        } else {
            var set = new TreeSet<String>();
            resultMap.put("", set);
            getSet = (ignore) -> set;
        }

        for (ClassFile file : jar.parseAllTypes()) {
            var requirements = getSet.apply(file.getClassName());
            for (String type : file.getTypesReferredTo()) {
                var typeName = JavaTypeUtils.cleanArrayTypeName(type);
                if (JavaTypeUtils.isPrimitiveJavaType(typeName)) continue;
                if (!JavaTypeUtils.isReferenceType(typeName)) {
                    typeName = 'L' + typeName + ';';
                }
                if (!JavaTypeUtils.mayBeJavaStdLibType(typeName) &&
                        (perClass || !jarTypes.contains(typeName))) {
                    requirements.add(typeName);
                }
            }
        }
        return resultMap;
    }

    public interface TypeVisitor {
        void startJar(File jar);

        void startType(String type);

        void onRequiredType(String typeName);

        void onDone();
    }

    private final class DefaultTypeVisitor implements TypeVisitor {

        private File jar;
        private int count;
        private boolean perClass;

        @Override
        public void startJar(File jar) {
            log.println("Required type(s) for " + jar + ':');
            this.jar = jar;
            count = 0;
        }

        @Override
        public void startType(String type) {
            perClass = true;
            log.println("  - Type " + typeNameToClassName(type) + ':');
        }

        @Override
        public void onRequiredType(String typeName) {
            log.println("    * " + typeNameToClassName(typeName));
            count++;
        }

        @Override
        public void onDone() {
            if (perClass) {
                log.println("");
                return;
            }
            if (count == 0) {
                log.println(() -> "  " + jar + " has no type requirements.");
            } else {
                log.println(() -> "  total " + count +
                        " type" + (count == 1 ? "" : "s") + " listed.");
            }
        }
    }
}
