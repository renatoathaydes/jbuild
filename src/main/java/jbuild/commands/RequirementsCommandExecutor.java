package jbuild.commands;

import jbuild.classes.model.ClassFile;
import jbuild.java.Jar;
import jbuild.log.JBuildLog;
import jbuild.util.JavaTypeUtils;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;

import static java.util.concurrent.CompletableFuture.supplyAsync;
import static jbuild.util.JavaTypeUtils.typeNameToClassName;

public class RequirementsCommandExecutor {

    private final JBuildLog log;
    private final MissingTypeVisitor missingTypeVisitor;

    public RequirementsCommandExecutor(
            JBuildLog log,
            MissingTypeVisitor visitor) {
        this.log = log;
        this.missingTypeVisitor = visitor;
    }

    private RequirementsCommandExecutor(
            JBuildLog log) {
        this.log = log;
        this.missingTypeVisitor = new JBuildMissingTypeVisitor();
    }

    public static RequirementsCommandExecutor createDefault(JBuildLog log) {
        return new RequirementsCommandExecutor(log);
    }

    public CompletionStage<Void> execute(Set<String> files) {
        var jarLoader = new Jar.Loader(log);

        // there can be no interleaving visiting jars
        var reporterThread = Executors.newSingleThreadExecutor();

        var futures = files.stream().map(file ->
                        jarLoader.lazyLoad(new File(file)).thenCompose(
                                jar -> supplyAsync(() -> typesRequiredBy(jar))
                                        .thenAcceptAsync(requiredTypes -> visit(jar, requiredTypes), reporterThread))
                ).map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures).whenComplete((_1, _2) -> reporterThread.shutdown());
    }

    private void visit(Jar jarFile, Set<String> requiredTypes) {
        missingTypeVisitor.startJar(jarFile.file);
        for (String requiredType : requiredTypes) {
            missingTypeVisitor.onMissingType(requiredType);
        }
        missingTypeVisitor.onDone();
    }

    private Set<String> typesRequiredBy(Jar jar) {
        var jarTypes = jar.types;
        var result = new TreeSet<String>();
        for (ClassFile file : jar.parseAllTypes()) {
            for (String type : file.getTypesReferredTo()) {
                var typeName = JavaTypeUtils.cleanArrayTypeName(type);
                if (JavaTypeUtils.isPrimitiveJavaType(typeName)) continue;
                if (!JavaTypeUtils.isReferenceType(typeName)) {
                    typeName = 'L' + typeName + ';';
                }
                if (!JavaTypeUtils.mayBeJavaStdLibType(typeName) &&
                        !jarTypes.contains(typeName)) {
                    result.add(typeName);
                }
            }
        }
        return result;
    }

    public interface MissingTypeVisitor {
        void startJar(File jar);

        void onMissingType(String typeName);

        void onDone();
    }

    private final class JBuildMissingTypeVisitor implements MissingTypeVisitor {

        private File jar;
        private int count;

        @Override
        public void startJar(File jar) {
            log.println("Required type(s) for " + jar + ":");
            this.jar = jar;
            count = 0;
        }

        @Override
        public void onMissingType(String typeName) {
            log.println("  * " + typeNameToClassName(typeName));
            count++;
        }

        @Override
        public void onDone() {
            if (count == 0) {
                log.println(() -> jar + " has no type requirements.");
            } else {
                log.println(() -> "  total " + count +
                        " type" + (count == 1 ? "" : "s") + " listed.");
            }
        }
    }
}
