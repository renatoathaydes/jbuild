package jbuild.java.tools;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static jbuild.java.JavapOutputParserTest.javapParse;
import static jbuild.java.tools.Tools.verifyToolSuccessful;
import static org.assertj.core.api.Assertions.assertThat;

public class JavacToolTest {

    @Test
    void canCompileSingleJavaFile() throws IOException {
        var dir = Files.createTempDirectory(JavacToolTest.class.getName());
        var javaSrc = dir.resolve("src/my/JavaClass.java");
        assertThat(javaSrc.getParent().toFile().mkdirs()).isTrue();

        var outDir = dir.resolve("out").toFile().getAbsoluteFile();
        var expectedClassFile = dir.resolve("out/my/JavaClass.class");

        write(javaSrc, "package my;\n" +
                "public class JavaClass {\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(\"hello world\");\n" +
                "    }\n" +
                "}");

        var result = Tools.Javac.create().compile(Set.of(javaSrc.toString()), outDir.getPath(), "");
        verifyToolSuccessful("javac", result);

        assertThat(outDir).isDirectory();
        assertThat(expectedClassFile).isNotEmptyFile();

        // verify that the class file was generated as expected
        var types = javapParse(outDir.toString(), "my.JavaClass");
        assertThat(types.keySet()).containsExactlyInAnyOrder("Lmy/JavaClass;");
    }

    private static void write(Path path, String contents) throws IOException {
        Files.write(path, contents.getBytes(UTF_8));
    }

}
