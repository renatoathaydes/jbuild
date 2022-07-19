package jbuild.cli;

import jbuild.errors.JBuildException;
import jbuild.log.JBuildLog;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;

final class JBuildFileRunner {

    private final JBuildLog log;
    private final CommandRunner commandRunner;
    private final FileReader fileReader;

    JBuildFileRunner(JBuildLog log, CommandRunner commandRunner) {
        this(log, commandRunner, createFileReader());
    }

    JBuildFileRunner(JBuildLog log, CommandRunner commandRunner, FileReader fileReader) {
        this.log = log;
        this.commandRunner = commandRunner;
        this.fileReader = fileReader;
    }

    void run(Options options) throws Exception {
        var file = new File("jbuild.txt");
        if (file.isFile()) {
            log.verbosePrintln("Running JBuild text file.");
            runTxtFile(file, options);
        } else {
            throw new JBuildException("No command provided and no JBuild file can be found. " +
                    "Use -h to see JBuild usage.", ACTION_ERROR);
        }
    }

    private void runTxtFile(File file, Options options) throws Exception {
        var currentLine = new StringBuilder(256);
        for (var line : fileReader.readLines(file.toString(), UTF_8)) {
            var maybeContinuesPrevLine = line.startsWith(" ");
            line = line.trim();
            if (line.isBlank()) {
                runLine(consume(currentLine), options);
            } else {
                if (line.startsWith("#")) continue;
                if (!maybeContinuesPrevLine) {
                    runLine(consume(currentLine), options);
                }
                currentLine.append(' ').append(line);
            }
        }
        if (currentLine.length() != 0) {
            runLine(consume(currentLine), options);
        }
    }

    private void runLine(String currentLine, Options options) throws Exception {
        if (currentLine.isBlank()) return;
        log.verbosePrintln(() -> "Collected JBuild command: " + currentLine);
        var lineOptions = Options.parse(currentLine.split("\\s+"));
        commandRunner.run(lineOptions.withGenericOptions(options));
    }

    private static String consume(StringBuilder currentLine) {
        var string = currentLine.toString();
        currentLine.delete(0, currentLine.length());
        return string;
    }

    private static FileReader createFileReader() {
        return (file, charset) -> Files.readAllLines(Paths.get(file), charset);
    }

}

interface FileReader {
    List<String> readLines(String file, Charset charset) throws IOException;
}

@FunctionalInterface
interface CommandRunner {
    void run(Options options) throws Exception;
}
