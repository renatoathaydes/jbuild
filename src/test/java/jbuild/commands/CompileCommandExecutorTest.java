package jbuild.commands;

import jbuild.java.JavapOutputParser;
import jbuild.log.JBuildLog;
import jbuild.util.Either;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
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
                Either.left(buildDir.toString()),
                "",
                "");

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
                Either.left(buildDir.toString()),
                "",
                "");

        verifyToolSuccessful("compile", result.getCompileResult());
        assertThat(result.getJarResult()).isNotPresent();

        // then, compile Bar into a jar, using buildDir as its classpath
        result = command.compile(
                Set.of(src2.toString()),
                Either.right(jar.toString()),
                "",
                buildDir.toString());

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

}
