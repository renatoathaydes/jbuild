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

        int totalFiles;
        try (var output = new FileWriter(outputFile, StandardCharsets.UTF_8)) {
            totalFiles = run(rootDir, output);
        }
        System.out.println("Found " + totalFiles + " file(s).");
    }

    private static int run(Path dir, FileWriter output) throws IOException {
        var total = 0;
        var files = dir.toFile().list();
        if (files != null && files.length > 0) {
            for (var file : files) {
                var path = dir.resolve(file);
                var attributes = Files.readAttributes(path, BasicFileAttributes.class);
                if (attributes.isDirectory()) {
                    total += run(path, output);
                } else {
                    output.write(path.toString());
                    output.write(EOL);
                    total++;
                }
            }
        }
        return total;
    }
}