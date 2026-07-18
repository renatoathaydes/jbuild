package jbuild.util;

import jbuild.TestSystemProperties;
import jbuild.classes.model.ClassFile;
import jbuild.classes.parser.JBuildClassFileParser;
import jbuild.java.tools.Tools;
import jbuild.log.JBuildLog;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

import static java.nio.charset.StandardCharsets.UTF_8;
import static jbuild.java.tools.Tools.verifyToolSuccessful;
import static org.assertj.core.api.Assertions.assertThat;

public final class TestHelper {
    public interface ClassPathOption {
        enum Option implements ClassPathOption {INHERIT, NONE}

        final class Explicit implements ClassPathOption {
            public final String classpath;

            public Explicit(String classpath) {
                this.classpath = classpath;
            }
        }
    }

    public static Map.Entry<JBuildLog, ByteArrayOutputStream> createLog(boolean verbose) {
        var stream = new ByteArrayOutputStream(512);
        var printer = new PrintStream(stream);
        return Map.entry(new JBuildLog(printer, verbose), stream);
    }

    public static ClassFile compileJavaClassFile(String name,
                                                 String classText,
                                                 ClassPathOption classPathOption)
            throws IOException {
        var parser = new JBuildClassFileParser();
        var file = compileJavaClass(name, classText, classPathOption);
        try (var stream = Files.newInputStream(file)) {
            return parser.parse(stream);
        }
    }

    public static Path compileJavaClass(String name,
                                        String classText,
                                        ClassPathOption classPathOption)
            throws IOException {
        var nameParts = name.split("\\.");
        var className = nameParts[nameParts.length - 1];
        var packageParts = new ArrayList<String>(nameParts.length);
        packageParts.add("out");
        packageParts.addAll(Arrays.asList(nameParts).subList(0, nameParts.length - 1));
        var dir = Files.createTempDirectory(TestHelper.class.getName());
        var javaFile = dir.resolve(className + ".java");
        Files.write(javaFile, classText.getBytes(UTF_8));
        var outDir = dir.resolve("out").toFile().getAbsoluteFile();
        var expectedClassFile = dir.resolve(String.join(File.separator, packageParts))
                .resolve(className + ".class");
        var result = Tools.Javac.create(createLog(false).getKey())
                .compile(Set.of(javaFile.toString()), outDir.getPath(),
                        resolve(classPathOption), "", "", List.of());
        verifyToolSuccessful("javac", result);
        return expectedClassFile;
    }

    private static String resolve(ClassPathOption option) {
        if (option == ClassPathOption.Option.NONE) return "";
        if (option == ClassPathOption.Option.INHERIT) return resolveJBuildClasspath();
        if (option instanceof ClassPathOption.Explicit) {
            return ((ClassPathOption.Explicit) option).classpath;
        }
        throw new IllegalArgumentException("cannot handle: " + option);
    }

    private static String resolveJBuildClasspath() {
        TestSystemProperties.validate("jbApiJar", TestSystemProperties.jbApiJar);
        return TestSystemProperties.jbApiJar.getAbsolutePath();
    }

    public static Map<String, String> parseExpectedZipContentsWithSha1(String resource) throws IOException {
        var result = new LinkedHashMap<String, String>();
        try (var stream = TestHelper.class.getResourceAsStream(resource)) {
            assert stream != null : "resource not found: " + resource;
            var reader = new BufferedReader(new InputStreamReader(stream, UTF_8));
            reader.lines().forEach(line -> {
                var parts = line.split(":\\s+", 2);
                result.put(parts[0], parts[1]);
            });
        }
        return result;
    }

    public static void assertIsZipContaining(Path jar, Map<String, String> sha1ByEntry) throws IOException {
        var index = 0;
        var entryIterator = sha1ByEntry.entrySet().iterator();
        var extraEntries = new ArrayList<String>();
        try (var zip = new ZipFile(jar.toFile())) {
            var zipEntries = zip.entries().asIterator();
            while (zipEntries.hasNext()) {
                var zipEntry = zipEntries.next();
                if (index < sha1ByEntry.size()) {
                    var entry = entryIterator.next();
                    var entryName = entry.getKey();
                    final var theIndex = index;
                    assertThat(zipEntry.getName())
                            .withFailMessage(() -> "Entry at index " + theIndex + " should be '" + entryName +
                                    "', but was '" + zipEntry.getName() + "'")
                            .isEqualTo(entryName);
                    byte[] actualBytes = zip.getInputStream(zipEntry).readAllBytes();
                    var zipEntrySha1 = SHA1.computeSha1HexString(actualBytes);
                    assertThat(zipEntrySha1)
                            .withFailMessage(() -> "Entry at index " + theIndex + ", '" + entryName +
                                    "' SHA1 mismatch: " + zipEntrySha1 + " != " + entry.getValue() +
                                    "\nEntry bytes: " + textFor(actualBytes))
                            .isEqualTo(entry.getValue());
                    index++;
                } else {
                    extraEntries.add(zipEntry.getName());
                }
            }
        }
        if (!extraEntries.isEmpty()) {
            throw new RuntimeException("Expected " + sha1ByEntry.size() +
                    " entries, but found the following extra entries:" + extraEntries);
        }
        if (entryIterator.hasNext()) {
            var missingEntries = new ArrayList<String>();
            entryIterator.forEachRemaining(e -> missingEntries.add(e.getKey()));
            throw new RuntimeException("Missing entries in the jar: " + missingEntries);
        }
    }

    private static String textFor(byte[] actualBytes) {
        if (actualBytes.length > 4096) {
            var subArray = new byte[4096];
            System.arraycopy(actualBytes, 0, subArray, 0, 4096);
            return Arrays.toString(subArray) + "...";
        }
        return Arrays.toString(actualBytes);
    }
}
