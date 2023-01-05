package jbuild.commands;

import jbuild.classes.model.ClassFile;
import jbuild.java.Jar;
import jbuild.log.JBuildLog;
import jbuild.util.JavaTypeUtils;

import java.io.File;
import java.util.List;
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
                .map(file -> jarLoader.lazyLoad(new File(file))
                        .thenCompose(jar -> supplyAsync(() -> typesRequiredBy(jar, perClass), reporterThread)
                                .thenAcceptAsync(requiredTypes -> visit(jar, requiredTypes, perClass), reporterThread)))
                .map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures).whenComplete((_1, _2) -> {
            reporterThread.shutdown();
            jarLoader.close();
        });
    }

    private void visit(Jar jarFile, Map<String, TypeRequirements> requiredTypes, boolean perClass) {
        missingTypeVisitor.startJar(jarFile.file);
        if (perClass) {
            requiredTypes.forEach(missingTypeVisitor::handleTypeRequirements);
        } else
            for (var types : requiredTypes.values()) {
                missingTypeVisitor.handleJarRequirements(types.requirements);
            }
        missingTypeVisitor.onDone();
    }

    private Map<String, TypeRequirements> typesRequiredBy(Jar jar, boolean perClass) {
        var jarTypes = jar.types;
        var resultMap = new TreeMap<String, TypeRequirements>();
        Function<ClassFile, TreeSet<String>> getSet;
        if (perClass) {
            getSet = (classFile) -> {
                var set = new TreeSet<String>();
                resultMap.put(classFile.getTypeName(), new TypeRequirements(classFile, set));
                return set;
            };
        } else {
            var set = new TreeSet<String>();
            resultMap.put("", new TypeRequirements(null, set));
            getSet = (ignore) -> set;
        }

        for (ClassFile file : jar.parseAllTypes()) {
            var requirements = getSet.apply(file);
            for (String typeItem : file.getTypesReferredTo()) {
                // type may be a type descriptor
                List<String> allTypes;
                if (typeItem.contains("(")) {
                    allTypes = JavaTypeUtils.parseMethodArgumentsTypes(typeItem);
                } else {
                    allTypes = List.of(typeItem);
                }
                for (var type : allTypes) {
                    var typeName = JavaTypeUtils.cleanArrayTypeName(type);
                    if (JavaTypeUtils.isPrimitiveJavaType(typeName))
                        continue;
                    if (!JavaTypeUtils.isReferenceType(typeName)) {
                        typeName = 'L' + typeName + ';';
                    }
                    if (!JavaTypeUtils.mayBeJavaStdLibType(typeName) && (perClass || !jarTypes.contains(typeName))) {
                        requirements.add(typeName);
                    }
                }
            }
        }
        return resultMap;
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
        void startJar(File jar);

        void handleTypeRequirements(String type, TypeRequirements typeRequirements);

        void handleJarRequirements(TreeSet<String> types);

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
        public void handleTypeRequirements(String type, TypeRequirements typeRequirements) {
            perClass = true;
            log.println(
                    "  - " + typeNameToClassName(type) + '(' + typeRequirements.classFile.getSourceFile() + ')' + ':');
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
                log.println(() -> "  " + jar + " has no type requirements.");
            } else {
                log.println(() -> "  total " + count +
                        " type" + (count == 1 ? "" : "s") + " listed.");
            }
        }
    }
}
