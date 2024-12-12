package jb;

import jbuild.api.JBuildException;
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
    static final String INPUT = "src/main/template/jbuild/Version.java";
    static final String OUTPUT = "src/main/java/jbuild/Version.java";

    private final JbConfig config;

    public GenerateJBuildVersion(JbConfig config) {
        this.config = config;
    }

    @Override
    public List<String> inputs() {
        return List.of(INPUT);
    }

    @Override
    public List<String> outputs() {
        return List.of(OUTPUT);
    }

    @Override
    public List<String> dependents() {
        return List.of("compile", "publicationCompile");
    }

    @Override
    public void run(String... args) throws IOException {
        var dir = new File(OUTPUT).getParentFile();
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) {
            throw new JBuildException("Cannot create output directory for " + OUTPUT, IO_WRITE);
        }
        writeString(Paths.get(OUTPUT),
                readAllLines(Paths.get(INPUT))
                        .stream().map((line) -> line.replace("%VERSION%", config.version))
                        .collect(Collectors.joining(System.lineSeparator())));
    }
}
