package jb;

import jbuild.api.JBuildException;
import jbuild.api.JbTask;
import jbuild.api.JbTaskInfo;
import jbuild.api.TaskPhase;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import static java.nio.file.Files.readAllLines;
import static java.nio.file.Files.writeString;
import static jbuild.api.JBuildException.ErrorCause.IO_WRITE;

@JbTaskInfo(name = "generateJBuildVersion",
        description = "Generates a Version file so JBuild can report its own version.",
        inputs = GenerateJBuildVersion.INPUT,
        outputs = GenerateJBuildVersion.OUTPUT,
        phase = @TaskPhase(name = "setup"),
        dependents = {"compile", "publicationCompile"})
public class GenerateJBuildVersion implements JbTask {
    static final String INPUT = "../src/main/template/jbuild/Version.java";
    static final String OUTPUT = "../src/main/java/jbuild/Version.java";

    @Override
    public void run(String... args) throws IOException {
        var dir = new File(OUTPUT).getParentFile();
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) {
            throw new JBuildException("Cannot create output directory for " + OUTPUT, IO_WRITE);
        }
        writeString(Paths.get(OUTPUT),
                readAllLines(Paths.get(INPUT))
                        .stream().map((line) -> line.replace("%VERSION%", "1.0"))
                        .collect(Collectors.joining(System.lineSeparator())));
    }
}
