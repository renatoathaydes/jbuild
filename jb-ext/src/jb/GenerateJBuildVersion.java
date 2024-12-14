package jb;

import jbuild.api.JBuildException;
import jbuild.api.JBuildLogger;
import jbuild.api.JbTask;
import jbuild.api.JbTaskInfo;
import jbuild.api.TaskPhase;
import jbuild.api.config.JbConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.file.Files.readAllLines;
import static java.nio.file.Files.writeString;
import static jbuild.api.JBuildException.ErrorCause.IO_WRITE;

@JbTaskInfo(name = "generateJBuildVersion",
        description = "Generates a Version file so JBuild can report its own version.",
        phase = @TaskPhase(name = "codegen", index = 200))
public class GenerateJBuildVersion implements JbTask {

    static final String JAVA_INPUT = "src/main/template/jbuild/Version.java";
    static final String MANIFEST_INPUT = "src/main/template/MANIFEST.txt";
    static final String JAVA_OUTPUT = "src/main/java/jbuild/Version.java";

    private final JbConfig config;
    private final JBuildLogger logger;

    public GenerateJBuildVersion(JbConfig config,
                                 JBuildLogger logger) {
        this.config = config;
        this.logger = logger;
    }

    @Override
    public List<String> inputs() {
        return List.of(JAVA_INPUT, MANIFEST_INPUT);
    }

    @Override
    public List<String> outputs() {
        return List.of(JAVA_OUTPUT, config.manifest);
    }

    @Override
    public List<String> dependents() {
        return List.of("compile", "publicationCompile");
    }

    @Override
    public void run(String... args) throws IOException {
        generateFile(JAVA_INPUT, JAVA_OUTPUT);
        generateFile(MANIFEST_INPUT, config.manifest);
    }

    private void generateFile(String in, String out) throws IOException {
        var outDir = new File(out).getParentFile();
        if (outDir == null || (!outDir.isDirectory() && !outDir.mkdirs())) {
            throw new JBuildException("Cannot create output directory for " + out, IO_WRITE);
        }
        writeString(Paths.get(out),
                readAllLines(Paths.get(in))
                        .stream().map((line) -> line.replace("%VERSION%", config.version))
                        .collect(Collectors.joining(System.lineSeparator())));

        logger.verbosePrintln(() -> "Generated " + out + " from " + in);
    }

}
