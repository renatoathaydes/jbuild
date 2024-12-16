package jbuild.util;

import jbuild.TestSystemProperties;
import jbuild.classes.JBuildClassFileParser;
import jbuild.classes.model.ClassFile;
import jbuild.java.tools.Tools;
import jbuild.log.JBuildLog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static jbuild.java.tools.Tools.verifyToolSuccessful;

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
                        resolve(classPathOption), List.of());
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
}
