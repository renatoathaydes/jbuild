package jbuild.commands;

import jbuild.java.CallHierarchyVisitor;
import jbuild.java.ClassGraph;
import jbuild.java.Jar;
import jbuild.java.JarSet;
import jbuild.java.code.Code;
import jbuild.java.code.Definition;
import jbuild.log.JBuildLog;
import jbuild.util.Describable;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;

import static java.util.concurrent.CompletableFuture.runAsync;
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
        var visitor = new RequirementsVisitor(log, missingTypeVisitor);

        // there can be no interleaving visiting jars
        var reporterThread = Executors.newSingleThreadExecutor();

        var futures = files.stream().map(file ->
                        jarLoader.lazyLoad(new File(file)).thenCompose(jar ->
                                JarSet.of(jar).toClassGraph().thenComposeAsync(graph ->
                                        runAsync(() -> new CallHierarchyVisitor(graph)
                                                        .visit(Set.of(jar.file), visitor),
                                                reporterThread)
                                ))
                ).map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
                .thenComposeAsync((ignore) -> runAsync(missingTypeVisitor::onDone, reporterThread))
                .whenComplete((_1, _2) -> reporterThread.shutdown());
    }

    public interface MissingTypeVisitor {
        void startJar(File jar);

        void onMissingType(List<Describable> referenceChain,
                           String typeName);

        void onDone();
    }

    private final class JBuildMissingTypeVisitor implements MissingTypeVisitor {

        private File jar;
        private boolean reportedJar;
        private final Set<String> types = new HashSet<>();

        @Override
        public void startJar(File jar) {
            if (this.jar != null) {
                reportJarRequirementSize();
            }
            this.jar = jar;
            reportedJar = false;
            types.clear();
        }

        @Override
        public void onMissingType(List<Describable> referenceChain, String typeName) {
            if (!reportedJar) {
                log.println("Required type(s) for " + jar + ":");
                reportedJar = true;
            }
            if (log.isVerbose()) {
                if (!referenceChain.isEmpty()) {
                    log.print("  - at ");
                    var builder = new StringBuilder(256);
                    var last = referenceChain.get(referenceChain.size() - 1);
                    for (var describable : referenceChain) {
                        describable.describe(builder, true);
                        if (describable != last) {
                            builder.append(" -> ");
                        }
                    }
                    log.println(builder);
                }
            }
            if (types.add(typeName) || log.isVerbose()) {
                log.println("  * " + typeNameToClassName(typeName));
            }
        }

        @Override
        public void onDone() {
            reportJarRequirementSize();
        }

        private void reportJarRequirementSize() {
            if (types.isEmpty()) {
                log.println(() -> jar + " has no type requirements.");
            } else {
                log.println(() -> "  total " + types.size() +
                        " type" + (types.size() == 1 ? "" : "s") + " listed.");
            }
        }
    }

    private static final class RequirementsVisitor implements CallHierarchyVisitor.Visitor {

        private final JBuildLog log;
        private final MissingTypeVisitor delegate;

        public RequirementsVisitor(JBuildLog log,
                                   MissingTypeVisitor delegate) {
            this.log = log;
            this.delegate = delegate;
        }

        @Override
        public void startJar(File jar) {
            log.verbosePrintln(() -> "Checking jar: " + jar);
            delegate.startJar(jar);
        }

        @Override
        public void visit(List<Describable> referenceChain,
                          ClassGraph.TypeDefinitionLocation typeDefinitionLocation) {
            log.verbosePrintln(() -> "Visiting " + typeDefinitionLocation);
        }

        @Override
        public void visit(List<Describable> referenceChain,
                          Definition definition) {
            log.verbosePrintln(() -> "Visiting " + definition);
        }

        @Override
        public void visit(List<Describable> referenceChain,
                          Code code) {
            log.verbosePrintln(() -> "Visiting " + code);
        }

        @Override
        public void onMissingType(List<Describable> referenceChain,
                                  String typeName) {
            log.verbosePrintln(() -> "Visiting " + typeName);
            delegate.onMissingType(referenceChain, typeName);
        }

        @Override
        public void onMissingMethod(List<Describable> referenceChain,
                                    ClassGraph.TypeDefinitionLocation typeDefinitionLocation,
                                    Code.Method method) {
            log.verbosePrintln(() -> "Missing: " + method);
        }

        @Override
        public void onMissingField(List<Describable> referenceChain,
                                   ClassGraph.TypeDefinitionLocation typeDefinitionLocation,
                                   Code.Field field) {
            log.verbosePrintln(() -> "Missing: " + field);
        }

        @Override
        public void onMissingField(List<Describable> referenceChain,
                                   String javaTypeName,
                                   Code.Field field) {
            log.verbosePrintln(() -> "Missing: " + field);
        }
    }
}
