package jbuild.commands;

import jbuild.java.JavapOutputParser;
import jbuild.log.JBuildLog;
import jbuild.util.Either;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

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
                Set.of(),
                Either.left(buildDir.toString()),
                "",
                "",
                List.of());

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
                Set.of(),
                Either.left(buildDir.toString()),
                "",
                "",
                List.of());

        verifyToolSuccessful("compile", result.getCompileResult());
        assertThat(result.getJarResult()).isNotPresent();

        // then, compile Bar into a jar, using buildDir as its classpath
        result = command.compile(
                Set.of(src2.toString()),
                Set.of(),
                Either.right(jar.toString()),
                "",
                buildDir.toString(),
                List.of());

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
        System.out.println("outDir=" + outDir);

        command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of(resDir.toFile().getAbsolutePath()),
                Either.left(outDir.toFile().getAbsolutePath()),
                "",
                "",
                List.of());

        var outMyRes = outDir.resolve(myRes.getFileName().toString());
        var outMyFooRes = outDir.resolve(Paths.get("foo", myFooRes.getFileName().toString()));

        assertThat(outDir).isDirectory();
        System.out.println("Files in outDir: " + Arrays.toString(outDir.toFile().list()));
        assertThat(outMyRes).isRegularFile().hasContent("hello");
        assertThat(outMyFooRes).isRegularFile().hasContent("foo bar");
    }

}
