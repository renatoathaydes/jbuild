package jbuild.commands;

import jbuild.java.JavapOutputParser;
import jbuild.java.tools.Tools;
import jbuild.log.JBuildLog;
import jbuild.util.Either;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

        verifyToolSuccessful("compile", result.getCompileResult());
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

        verifyToolSuccessful("compile", result.getCompileResult());
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

        verifyToolSuccessful("compile", result.getCompileResult());
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

        verifyToolSuccessful("compile", firstResult.getCompileResult());
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

        verifyToolSuccessful("compile", secondResult.getCompileResult());
        assertThat(secondResult.getJarResult()).isPresent();
        verifyToolSuccessful("jar", secondResult.getJarResult().get());

        // Run the jar to make sure the change was handled correctly
        var javaProc = new ProcessBuilder("java", "-jar", jar.toString()).start();
        var javaExitCode = javaProc.waitFor();

        assertThat(javaExitCode).isZero();
        assertThat(javaProc.getInputStream()).hasContent("bye world");
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

        verifyToolSuccessful("compile", firstResult.getCompileResult());
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

        verifyToolSuccessful("compile", secondResult.getCompileResult());
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
                Set.of(),
                Either.right(jar.toFile().getAbsolutePath()),
                "Main",
                false,
                "",
                List.of(),
                null);

        verifyToolSuccessful("compile", firstResult.getCompileResult());
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
                Set.of(),
                Either.right(jar.toFile().getAbsolutePath()),
                "Main",
                false,
                "",
                List.of(),
                new IncrementalChanges(Set.of(utilJava.toString()), Set.of(mainJava.toString())));

        verifyToolSuccessful("compile", secondResult.getCompileResult());
        assertThat(secondResult.getJarResult()).isPresent();
        verifyToolSuccessful("jar", secondResult.getJarResult().get());

        // Run the jar to make sure the change was handled correctly
        var javaProc = new ProcessBuilder("java", "-jar", jar.toString()).start();
        var javaExitCode = javaProc.waitFor();

        assertThat(javaExitCode).isZero();
        assertThat(javaProc.getInputStream()).hasContent("Main hello world");

        // make sure the jar was patched correctly
        var patchedJarContents = Tools.Jar.create().listContents(jar.toString()).getStdoutLines()
                .collect(Collectors.toList());

        assertThat(patchedJarContents).containsExactly("META-INF/", "META-INF/MANIFEST.MF", "Main.class");
    }

}
