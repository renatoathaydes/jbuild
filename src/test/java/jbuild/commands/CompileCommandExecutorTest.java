package jbuild.commands;

import jbuild.TestSystemProperties;
import jbuild.java.JavapOutputParser;
import jbuild.java.tools.Tools;
import jbuild.log.JBuildLog;
import jbuild.util.Either;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipFile;

import static java.util.stream.Collectors.toList;
import static jbuild.java.JavapOutputParserTest.javap;
import static jbuild.java.tools.Tools.verifyToolSuccessful;
import static org.assertj.core.api.Assertions.assertThat;

public class CompileCommandExecutorTest {

    @Test
    void canCompileClassFiles() throws IOException {
        var bytesOut = new ByteArrayOutputStream();
        var out = new PrintStream(bytesOut);
        var log = new JBuildLog(out, false);
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var src1 = dir.resolve("src1");
        assert src1.toFile().mkdir();
        var fooJava = src1.resolve("Foo.java");
        Files.write(fooJava, List.of("public class Foo {}"));
        var src2 = dir.resolve("src2");
        assert src2.toFile().mkdir();
        var barJava = src2.resolve("Bar.java");
        Files.write(barJava, List.of("public class Bar {",
                "Bar() { new Foo(); }",
                "}"));
        var buildDir = dir.resolve("build");

        var result = command.compile(
                Set.of(src1.toString(), src2.toString()),
                Set.of("no-resources"),
                Either.left(buildDir.toString()),
                "",
                false,
                "",
                List.of(),
                null);

        assertThat(result.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", result.getCompileResult().get());
        assertThat(result.getJarResult()).isNotPresent();

        var fooClass = buildDir.resolve("Foo.class");
        var barClass = buildDir.resolve("Bar.class");

        var buildFiles = buildDir.toFile().listFiles();

        assertThat(buildFiles).containsExactlyInAnyOrder(fooClass.toFile(), barClass.toFile());

        var types = new JavapOutputParser(log).processJavapOutput(
                javap(buildDir.toString(), "Foo", "Bar"));

        assertThat(types.keySet()).contains("LFoo;", "LBar;");
    }

    @Test
    void canCompileClassFilesOnWorkingDir() throws IOException {
        var bytesOut = new ByteArrayOutputStream();
        var out = new PrintStream(bytesOut);
        var log = new JBuildLog(out, false);
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var src = dir.resolve("src");
        var pkg = src.resolve("pkg");
        assert pkg.toFile().mkdirs();
        var myClass = pkg.resolve("MyClass.java");
        Files.write(myClass, List.of("package pkg;\n" +
                "public class MyClass {}\n" +
                "final class OtherClass {}"));

        var buildDir = dir.resolve("build");

        // use workingDir argument, and all other paths relative to it
        var result = command.compile(
                dir.toString(),
                Set.of(),
                Set.of(),
                Either.left("build"),
                "",
                false,
                "",
                List.of(),
                null);

        assertThat(result.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", result.getCompileResult().get());
        assertThat(result.getJarResult()).isNotPresent();

        var myClassFile = buildDir.resolve(Paths.get("pkg", "MyClass.class"));
        var otherClassFile = buildDir.resolve(Paths.get("pkg", "OtherClass.class"));

        var buildFiles = buildDir.resolve("pkg").toFile().listFiles();

        assertThat(buildFiles).containsExactlyInAnyOrder(myClassFile.toFile(), otherClassFile.toFile());
    }

    @Test
    void canCompileToJarUsingClasspathOption() throws IOException {
        var bytesOut = new ByteArrayOutputStream();
        var out = new PrintStream(bytesOut);
        var log = new JBuildLog(out, false);
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var src1 = dir.resolve("src1");
        assert src1.toFile().mkdir();
        var fooJava = src1.resolve("Foo.java");
        Files.write(fooJava, List.of("public class Foo {}"));
        var src2 = dir.resolve("src2");
        assert src2.toFile().mkdir();
        var barJava = src2.resolve("Bar.java");
        Files.write(barJava, List.of("public class Bar {",
                "Bar() { new Foo(); }",
                "}"));
        var buildDir = dir.resolve("build");
        var jar = dir.resolve("lib.jar");

        // first, compile Foo into a simple class file
        var result = command.compile(
                Set.of(src1.toString()),
                Set.of("no-resources"),
                Either.left(buildDir.toString()),
                "",
                false,
                "",
                List.of(),
                null);

        assertThat(result.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", result.getCompileResult().get());
        assertThat(result.getJarResult()).isNotPresent();

        // then, compile Bar into a jar, using buildDir as its classpath
        result = command.compile(
                Set.of(src2.toString()),
                Set.of("no-resources"),
                Either.right(jar.toString()),
                "",
                false,
                buildDir.toString(),
                List.of(),
                null);

        assertThat(result.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", result.getCompileResult().get());
        assertThat(result.getJarResult()).isPresent();
        verifyToolSuccessful("jar", result.getJarResult().get());

        var fooClass = buildDir.resolve("Foo.class");
        var buildFiles = buildDir.toFile().listFiles();

        assertThat(buildFiles).containsExactlyInAnyOrder(fooClass.toFile());

        var types = new JavapOutputParser(log).processJavapOutput(
                javap(jar.toString(), "Bar"));

        assertThat(types.keySet()).contains("LBar;");
    }

    @Test
    void canCompileToJarOnWorkingDirUsingJbExtensionOption() throws IOException {
        var bytesOut = new ByteArrayOutputStream();
        var out = new PrintStream(bytesOut);
        var log = new JBuildLog(out, false);
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var rootDir = dir.resolve("mylib");
        var src = rootDir.resolve("src");
        var pkg = src.resolve("pkg");
        assert pkg.toFile().mkdirs();
        var barJava = pkg.resolve("MyExtension.java");
        Files.write(barJava, List.of(
                "package pkg;",
                "import jbuild.api.*;",
                "@JbTaskInfo(name = \"my-extension\")",
                "public class MyExtension implements JbTask {",
                "  @Override",
                "  public void run(String... args) {}",
                "}"));
        var jar = rootDir.resolve("mylib.jar");

        // compile jar as a jb extension
        var result = command.compile(
                rootDir.toString(),
                Set.of(),
                Set.of(),
                Either.right(""), // let the default jar name be used
                "",
                true,
                TestSystemProperties.jbApiJar.getAbsolutePath(),
                List.of(),
                null);

        assertThat(result.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", result.getCompileResult().get());
        assertThat(result.getJarResult()).isPresent();
        verifyToolSuccessful("jar", result.getJarResult().get());

        var jarResult = Tools.Jar.create().listContents(jar.toString());
        verifyToolSuccessful("jar tf", jarResult);
        assertThat(jarResult.getStdoutLines().collect(toList()))
                .containsExactly(
                        "META-INF/",
                        "META-INF/MANIFEST.MF",
                        "META-INF/jb/",
                        "META-INF/jb/jb-extension.yaml",
                        "pkg/",
                        "pkg/MyExtension.class");
    }

    @Test
    void canCopyResources() throws IOException {
        var bytesOut = new ByteArrayOutputStream();
        var out = new PrintStream(bytesOut);
        var log = new JBuildLog(out, false);
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());

        var src = dir.resolve("src");
        assert src.toFile().mkdir();
        var srcFooJava = src.resolve("Foo.java");
        Files.write(srcFooJava, List.of("public class Foo {}"));

        var resDir = dir.resolve("resources");
        assert resDir.toFile().mkdir();
        var fooJava = resDir.resolve("Foo.java");
        Files.write(fooJava, List.of("public class Foo {}"));
        var myRes = resDir.resolve("my-resource.txt");
        Files.write(myRes, List.of("hello"));

        var resFooDir = resDir.resolve("foo");
        assert resFooDir.toFile().mkdir();
        var myFooRes = resFooDir.resolve("my-foo.txt");
        Files.write(myFooRes, List.of("foo bar"));

        var outDir = dir.resolve("output");

        command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of(resDir.toFile().getAbsolutePath()),
                Either.left(outDir.toFile().getAbsolutePath()),
                "",
                false,
                "",
                List.of(),
                null);

        var outMyRes = outDir.resolve(myRes.getFileName().toString());
        var outMyFooRes = outDir.resolve(Paths.get("foo", myFooRes.getFileName().toString()));

        assertThat(outDir).isDirectory();
        assertThat(outMyRes).isRegularFile().hasContent("hello");
        assertThat(outMyFooRes).isRegularFile().hasContent("foo bar");
    }

    @Test
    void incrementalCompilationWithOnlyModifiedFile() throws Exception {
        var bytesOut = new ByteArrayOutputStream();
        var out = new PrintStream(bytesOut);
        var log = new JBuildLog(out, false);
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var jar = dir.resolve("lib.jar");
        var src = dir.resolve("src");
        assert src.toFile().mkdir();

        var utilJava = src.resolve("Util.java");
        Files.write(utilJava, List.of("class Util {",
                "  static String message() { return \"hello world\"; }",
                "}"));

        var mainJava = src.resolve("Main.java");
        Files.write(mainJava, List.of("class Main {",
                "  public static void main(String[] args) {",
                "    System.out.println(Util.message());",
                "  }",
                "}"));

        // initial compilation
        final var firstResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of(),
                Either.right(jar.toFile().getAbsolutePath()),
                "Main",
                false,
                "",
                List.of(),
                null);

        assertThat(firstResult.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", firstResult.getCompileResult().get());
        assertThat(firstResult.getJarResult()).isPresent();
        verifyToolSuccessful("jar", firstResult.getJarResult().get());
        assertThat(jar).isRegularFile();

        // change the Util class
        Files.write(utilJava, List.of("class Util {",
                "  static String message() { return \"bye world\"; }",
                "}"));

        // incremental compilation
        final var secondResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of(),
                Either.right(jar.toFile().getAbsolutePath()),
                "Main",
                false,
                "",
                List.of(),
                new IncrementalChanges(Set.of(), Set.of(utilJava.toString())));

        assertThat(secondResult.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", secondResult.getCompileResult().get());
        assertThat(secondResult.getJarResult()).isPresent();
        verifyToolSuccessful("jar", secondResult.getJarResult().get());

        // Run the jar to make sure the change was handled correctly
        var javaProc = new ProcessBuilder("java", "-jar", jar.toString()).start();
        var javaExitCode = javaProc.waitFor();

        assertThat(javaExitCode).isZero();
        assertThat(javaProc.getInputStream()).hasContent("bye world");
    }

    @Test
    void incrementalCompilationWithOnlyModifiedResourceFile() throws Exception {
        var bytesOut = new ByteArrayOutputStream();
        var out = new PrintStream(bytesOut);
        var log = new JBuildLog(out, false);
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var jar = dir.resolve("lib.jar");
        var src = dir.resolve("src");
        assert src.toFile().mkdir();
        var rsrc = dir.resolve("resources");
        assert rsrc.toFile().mkdir();

        var resource = rsrc.resolve("res.txt");
        Files.write(resource, List.of("my resource"));

        var mainJava = src.resolve("Main.java");
        Files.write(mainJava, List.of("class Main {",
                "  public static void main(String[] args) {",
                "    System.out.println(Main.class.getResource(\"res.txt\"));",
                "  }",
                "}"));

        // initial compilation
        final var firstResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of(rsrc.toFile().getAbsolutePath()),
                Either.right(jar.toFile().getAbsolutePath()),
                "Main",
                false,
                "",
                List.of(),
                null);

        assertThat(firstResult.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", firstResult.getCompileResult().get());
        assertThat(firstResult.getJarResult()).isPresent();
        verifyToolSuccessful("jar", firstResult.getJarResult().get());
        assertThat(jar).isRegularFile();

        // change the resource only
        Files.write(resource, List.of("new resource contents"));

        // incremental compilation
        final var secondResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of(rsrc.toFile().getAbsolutePath()),
                Either.right(jar.toFile().getAbsolutePath()),
                "Main",
                false,
                "",
                List.of(),
                new IncrementalChanges(Set.of(), Set.of(resource.toString())));

        assertThat(secondResult.getCompileResult()).isEmpty();
        assertThat(secondResult.getJarResult()).isPresent();
        verifyToolSuccessful("jar", secondResult.getJarResult().get());

        var patchedJarContents = Tools.Jar.create().listContents(jar.toString()).getStdoutLines()
                .collect(toList());

        assertThat(patchedJarContents).containsExactlyInAnyOrder("META-INF/", "META-INF/MANIFEST.MF",
                "Main.class", resource.getFileName().toString());

        assertThat(unzipEntry(jar, resource.getFileName().toString()))
                .isEqualToIgnoringNewLines("new resource contents");
    }

    @Test
    void incrementalCompilationWithOnlyDeletedResourceFileJar() throws Exception {
        var bytesOut = new ByteArrayOutputStream();
        var out = new PrintStream(bytesOut);
        var log = new JBuildLog(out, false);
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var jar = dir.resolve("lib.jar");
        var src = dir.resolve("src");
        assert src.toFile().mkdirs();
        var rsrc = dir.resolve("resources");
        assert rsrc.resolve("assets").toFile().mkdirs();

        var resource = rsrc.resolve(Paths.get("assets", "style.css"));
        Files.write(resource, List.of("body { color: red; }"));

        var mainJava = src.resolve("Main.java");
        Files.write(mainJava, List.of("class Main {",
                "  public static void main(String[] args) {",
                "    System.out.println(Main.class.getResource(\"assets/style.css\"));",
                "  }",
                "}"));

        // initial compilation
        final var firstResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of(rsrc.toFile().getAbsolutePath()),
                Either.right(jar.toFile().getAbsolutePath()),
                "Main",
                false,
                "",
                List.of(),
                null);

        assertThat(firstResult.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", firstResult.getCompileResult().get());
        assertThat(firstResult.getJarResult()).isPresent();
        verifyToolSuccessful("jar", firstResult.getJarResult().get());
        assertThat(jar).isRegularFile();

        // delete the resource dir
        assert resource.toFile().delete();
        assert resource.toFile().getParentFile().delete();

        // incremental compilation
        final var secondResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of(rsrc.toFile().getAbsolutePath()),
                Either.right(jar.toFile().getAbsolutePath()),
                "Main",
                false,
                "",
                List.of(),
                new IncrementalChanges(Set.of(resource.toString()), Set.of()));

        assertThat(secondResult.getCompileResult()).isEmpty();
        assertThat(secondResult.getJarResult()).isEmpty();

        var patchedJarContents = Tools.Jar.create().listContents(jar.toString()).getStdoutLines()
                .collect(toList());

        assertThat(patchedJarContents).containsExactlyInAnyOrder("META-INF/", "META-INF/MANIFEST.MF", "Main.class");
    }

    @Test
    void incrementalCompilationWithOnlyDeletedResourceFileDir() throws Exception {
        var bytesOut = new ByteArrayOutputStream();
        var out = new PrintStream(bytesOut);
        var log = new JBuildLog(out, false);
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var outputDir = dir.resolve("build");
        var src = dir.resolve("src");
        assert src.toFile().mkdirs();
        var rsrc = dir.resolve("resources");
        assert rsrc.resolve("assets").toFile().mkdirs();

        var resource = rsrc.resolve(Paths.get("assets", "style.css"));
        Files.write(resource, List.of("body { color: red; }"));

        var mainJava = src.resolve("Main.java");
        Files.write(mainJava, List.of("class Main {",
                "  public static void main(String[] args) {",
                "    System.out.println(Main.class.getResource(\"assets/style.css\"));",
                "  }",
                "}"));

        // initial compilation
        final var firstResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of(rsrc.toFile().getAbsolutePath()),
                Either.left(outputDir.toFile().getAbsolutePath()),
                "Main",
                false,
                "",
                List.of(),
                null);

        assertThat(firstResult.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", firstResult.getCompileResult().get());
        assertThat(firstResult.getJarResult()).isEmpty();
        assertThat(outputDir).isDirectory();

        // delete the resource dir
        assert resource.toFile().delete();
        assert resource.toFile().getParentFile().delete();

        // incremental compilation
        final var secondResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of(rsrc.toFile().getAbsolutePath()),
                Either.left(outputDir.toFile().getAbsolutePath()),
                "Main",
                false,
                "",
                List.of(),
                new IncrementalChanges(Set.of(resource.toString()), Set.of()));

        assertThat(secondResult.getCompileResult()).isEmpty();
        assertThat(secondResult.getJarResult()).isEmpty();

        var expectedOutput = Set.of(Paths.get("META-INF"),
                Paths.get("META-INF", "MANIFEST.MF"),
                Paths.get("Main.class"));

        assertThat(outputDir).isDirectoryRecursivelyContaining((file) ->
                expectedOutput.contains(outputDir.relativize(file)));
    }

    @Test
    void incrementalCompilationWithModifiedAndAddedFiles() throws Exception {
        var bytesOut = new ByteArrayOutputStream();
        var out = new PrintStream(bytesOut);
        var log = new JBuildLog(out, false);
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var jar = dir.resolve("lib.jar");
        var src = dir.resolve("src");
        assert src.toFile().mkdir();

        var utilJava = src.resolve("Util.java");
        Files.write(utilJava, List.of("class Util {",
                "  static String message() { return \"hello world\"; }",
                "}"));

        var mainJava = src.resolve("Main.java");
        Files.write(mainJava, List.of("class Main {",
                "  public static void main(String[] args) {",
                "    System.out.println(Util.message());",
                "  }",
                "}"));

        // initial compilation
        final var firstResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of(),
                Either.right(jar.toFile().getAbsolutePath()),
                "Main",
                false,
                "",
                List.of(),
                null);

        assertThat(firstResult.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", firstResult.getCompileResult().get());
        assertThat(firstResult.getJarResult()).isPresent();
        verifyToolSuccessful("jar", firstResult.getJarResult().get());
        assertThat(jar).isRegularFile();

        // change the Util class to delegate to another, new class
        Files.write(utilJava, List.of("class Util {",
                "  static String message() { return SecondaryUtil.msg(); }",
                "}"));

        // create the new class
        var util2Java = src.resolve("SecondaryUtil.java");
        Files.write(util2Java, List.of("class SecondaryUtil {",
                "  static String msg() { return \"secondary util message\"; }",
                "}"));

        // incremental compilation
        final var secondResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of(),
                Either.right(jar.toFile().getAbsolutePath()),
                "Main",
                false,
                "",
                List.of(),
                new IncrementalChanges(Set.of(),
                        Set.of(utilJava.toString(), util2Java.toString())));

        assertThat(secondResult.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", secondResult.getCompileResult().get());
        assertThat(secondResult.getJarResult()).isPresent();
        verifyToolSuccessful("jar", secondResult.getJarResult().get());

        // Run the jar to make sure the change was handled correctly
        var javaProc = new ProcessBuilder("java", "-jar", jar.toString()).start();
        var javaExitCode = javaProc.waitFor();

        assertThat(javaExitCode).isZero();
        assertThat(javaProc.getInputStream()).hasContent("secondary util message");
    }

    @Test
    void incrementalCompilationWithModifiedAndDeletedFile() throws Exception {
        var bytesOut = new ByteArrayOutputStream();
        var out = new PrintStream(bytesOut);
        var log = new JBuildLog(out, false);
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var jar = dir.resolve("lib.jar");
        var src = dir.resolve("src");
        assert src.toFile().mkdir();

        var utilJava = src.resolve("Util.java");
        Files.write(utilJava, List.of("class Util {",
                "  static String message() { return \"hello world\"; }",
                "}"));

        var mainJava = src.resolve("Main.java");
        Files.write(mainJava, List.of("class Main {",
                "  public static void main(String[] args) {",
                "    System.out.println(Util.message());",
                "  }",
                "}"));

        // initial compilation
        final var firstResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of("no-resources"),
                Either.right(jar.toFile().getAbsolutePath()),
                "Main",
                false,
                "",
                List.of(),
                null);

        assertThat(firstResult.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", firstResult.getCompileResult().get());
        assertThat(firstResult.getJarResult()).isPresent();
        verifyToolSuccessful("jar", firstResult.getJarResult().get());
        assertThat(jar).isRegularFile();

        // delete the Util class
        assert utilJava.toFile().delete();

        // make Main independent of Util
        Files.write(mainJava, List.of("class Main {",
                "  public static void main(String[] args) {",
                "    System.out.println(\"Main hello world\");",
                "  }",
                "}"));

        // incremental compilation
        final var secondResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of("no-resources"),
                Either.right(jar.toFile().getAbsolutePath()),
                "Main",
                false,
                "",
                List.of(),
                new IncrementalChanges(Set.of("Util.class"), Set.of(mainJava.toString())));

        assertThat(secondResult.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", secondResult.getCompileResult().get());
        assertThat(secondResult.getJarResult()).isPresent();
        verifyToolSuccessful("jar", secondResult.getJarResult().get());

        // Run the jar to make sure the change was handled correctly
        var javaProc = new ProcessBuilder("java", "-jar", jar.toString()).start();
        var javaExitCode = javaProc.waitFor();

        assertThat(javaExitCode).isZero();
        assertThat(javaProc.getInputStream()).hasContent("Main hello world");

        // make sure the jar was patched correctly
        var patchedJarContents = Tools.Jar.create().listContents(jar.toString()).getStdoutLines()
                .collect(toList());

        assertThat(patchedJarContents).containsExactly("META-INF/", "META-INF/MANIFEST.MF", "Main.class");
    }

    @Test
    void incrementalCompilationWithModifiedAndDeletedFileInPackageAndOutputDir() throws Exception {
        var bytesOut = new ByteArrayOutputStream();
        var out = new PrintStream(bytesOut);
        var log = new JBuildLog(out, false);
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var outputDir = dir.resolve("build");
        var src = dir.resolve("src");
        assert src.toFile().mkdir();
        var util = src.resolve("util");
        assert util.toFile().mkdir();
        var main = src.resolve("main");
        assert main.toFile().mkdir();

        var utilJava = util.resolve("Util.java");
        Files.write(utilJava, List.of("package util; public class Util {",
                "  public static String message() { return \"hello world\"; }",
                "}"));

        var mainJava = main.resolve("Main.java");
        Files.write(mainJava, List.of("package main; import util.Util; class Main {",
                "  public static void main(String[] args) {",
                "    System.out.println(Util.message());",
                "  }",
                "}"));

        // initial compilation
        final var firstResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of("no-resources"),
                Either.left(outputDir.toFile().getAbsolutePath()),
                "",
                false,
                "",
                List.of(),
                null);

        assertThat(firstResult.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", firstResult.getCompileResult().get());
        assertThat(firstResult.getJarResult()).isNotPresent();
        assertThat(outputDir).isDirectory();

        // delete the Util class
        assert utilJava.toFile().delete();
        assert util.toFile().delete();

        // make Main independent of Util
        Files.write(mainJava, List.of("package main; class Main {",
                "  public static void main(String[] args) {",
                "    System.out.println(\"Main hello world\");",
                "  }",
                "}"));

        // incremental compilation
        final var secondResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of("no-resources"),
                Either.left(outputDir.toFile().getAbsolutePath()),
                "",
                false,
                "",
                List.of(),
                new IncrementalChanges(Set.of(Paths.get("util", "Util.class").toString()), Set.of(mainJava.toString())));

        assertThat(secondResult.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", secondResult.getCompileResult().get());
        assertThat(secondResult.getJarResult()).isNotPresent();

        // Run the jar to make sure the change was handled correctly
        var javaProc = new ProcessBuilder("java", "-cp", outputDir.toString(), "main.Main").start();
        var javaExitCode = javaProc.waitFor();

        assertThat(javaExitCode).isZero();
        assertThat(javaProc.getInputStream()).hasContent("Main hello world");

        // make sure the class files were patched correctly
        assertThat(outputDir).isDirectoryContaining(path ->
                Objects.equals("main", path.getFileName().toString()));

        var outMain = outputDir.resolve("main");
        assertThat(outMain).isDirectoryContaining(path ->
                Objects.equals("Main.class", path.getFileName().toString()));
        assertThat(outMain.resolve("Main.class")).isRegularFile();
    }

    private static String unzipEntry(Path jar, String entry) throws IOException {
        try (var zip = new ZipFile(jar.toFile())) {
            var rsrcEntry = zip.getEntry(entry);
            return new String(zip.getInputStream(rsrcEntry).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

}
