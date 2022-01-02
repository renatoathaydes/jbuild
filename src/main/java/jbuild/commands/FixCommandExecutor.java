package jbuild.commands;

import jbuild.java.ClassGraph;
import jbuild.java.ClassGraphLoader;
import jbuild.java.code.Code;
import jbuild.java.code.Definition;
import jbuild.log.JBuildLog;

import java.util.stream.Collectors;

public final class FixCommandExecutor {

    private final JBuildLog log;
    private final ClassGraphLoader classGraphLoader;

    public FixCommandExecutor(JBuildLog log) {
        this(log, ClassGraphLoader.create(log));
    }

    public FixCommandExecutor(JBuildLog log,
                              ClassGraphLoader classGraphLoader) {
        this.log = log;
        this.classGraphLoader = classGraphLoader;
    }

    public void run(String inputDir, boolean interactive) {
        var classGraph = classGraphLoader.fromJarsInDirectory(inputDir);
        showInconsistencies(classGraph);
    }

    private void showInconsistencies(ClassGraph classGraph) {
        var jarsWithDuplicatedTypes = classGraph.getJarsByType().values().stream()
                .filter(jars -> jars.size() > 1)
                .collect(Collectors.toSet());

        if (jarsWithDuplicatedTypes.isEmpty()) {
            log.println("No conflicts found in the jars");
        } else {
            log.println("The following jars appear to conflict: " + jarsWithDuplicatedTypes);

            for (var jarsWithDuplicatedType : jarsWithDuplicatedTypes) {
                for (var jar : jarsWithDuplicatedType) {
                    var jarOk = true;
                    typesByJarLoop:
                    for (var entry : classGraph.getTypesByJar().get(jar).entrySet()) {
                        var name = entry.getKey();
                        var type = entry.getValue();
                        for (var ref : classGraph.referencesTo(new Code.Type(name))) {
                            var ok = ref.to.match(t -> true,
                                    f -> classGraph.exists(jar, type, new Definition.FieldDefinition(f.name, f.type)),
                                    m -> classGraph.exists(jar, type, new Definition.MethodDefinition(m.name, m.type)));
                            if (!ok) {
                                log.println("Cannot find\n  " + ref.type + " -> " + ref.to + "\n   in " + jar + "\n... this jar is not ok");
                                jarOk = false;
                                break typesByJarLoop;
                            }
                        }
                    }
                    if (jarOk) {
                        log.println("This jar is fine to use: " + jar);
                    }
                }
            }
        }
    }

}
