package jb;

import jbuild.api.JBuildException;
import jbuild.api.JBuildLogger;
import jbuild.api.JbTask;
import jbuild.api.JbTaskInfo;
import jbuild.api.TaskPhase;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static jbuild.api.JBuildException.ErrorCause.ACTION_ERROR;

@JbTaskInfo(name = "copyJbuildGroovyJar",
        description = "Copies the jbuild-groovy jar into the resources directory of JBuild.",
        phase = @TaskPhase(name = "build"))
public class CopyJbuildGroovyJar implements JbTask {

    private static final String OUT_JAR = "jbuild-groovy/build/jbuild-groovy.jar";
    private static final String TARGET_JAR = "src/main/resources/jbuild-groovy.jar";

    private final JBuildLogger logger;

    public CopyJbuildGroovyJar(JBuildLogger logger) {
        this.logger = logger;
    }

    @Override
    public List<String> inputs() {
        return List.of("jbuild-groovy/src/");
    }

    @Override
    public List<String> outputs() {
        return List.of(TARGET_JAR);
    }

    @Override
    public List<String> dependents() {
        return List.of("compile", "publicationCompile");
    }

    @Override
    public void run(String... args) throws IOException {
        logger.println("Running jbuild-groovy build");

        var pb = JbProcess.runJb("jb compile");
        pb.directory(new File("jbuild-groovy"));
        int exitCode;
        try {
            exitCode = pb.start().waitFor();
        } catch (InterruptedException e) {
            // should never happen
            throw new RuntimeException(e);
        }
        if (exitCode != 0) {
            throw new JBuildException("Failed to compile jbuild-groovy", ACTION_ERROR);
        }

        copyJar();

        logger.println("jbuild-groovy jar successfully copied to jbuild resources directory");
    }

    private void copyJar() throws IOException {
        logger.verbosePrintln(() -> "Copying jbuild-groovy jar into " + OUT_JAR);

        var targetJar = Paths.get(TARGET_JAR);

        // ensure the parent dir exists
        var ignore = targetJar.getParent().toFile().mkdirs();

        Files.copy(Paths.get(OUT_JAR), targetJar, StandardCopyOption.REPLACE_EXISTING);
    }
}
