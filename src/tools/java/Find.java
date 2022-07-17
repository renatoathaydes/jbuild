import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;

public class Find {
    private static final String EOL = System.getProperty("line.separator");

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new RuntimeException("Expected 2 arguments, got " + args.length);
        }
        var rootDir = Paths.get(args[0]);
        var outputFile = new File(args[1]);

        var outDir = outputFile.getParentFile();
        if (outDir != null) outDir.mkdirs();

        try (var output = new FileWriter(outputFile, StandardCharsets.UTF_8)) {
            run(rootDir, output);
        }
    }

    private static void run(Path dir, FileWriter output) throws IOException {
        var files = dir.toFile().list();
        if (files != null && files.length > 0) {
            for (var file : files) {
                var path = dir.resolve(file);
                var attributes = Files.readAttributes(path, BasicFileAttributes.class);
                if (attributes.isRegularFile()) {
                    output.write(path.toString());
                    output.write(EOL);
                } else if (attributes.isDirectory()) {
                    run(path, output);
                }
            }
        }
    }
}