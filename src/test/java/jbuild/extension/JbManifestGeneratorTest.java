package jbuild.extension;

import jbuild.api.JBuildException;
import jbuild.util.TestHelper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JbManifestGeneratorTest {

    @Test
    void canGenerateForTypicalJbTaskInfo() throws IOException {
        var classFile = TestHelper.compileJavaClassFile("Main", "" +
                "import jbuild.api.*;\n" +
                "@JbTaskInfo(name = \"my-task\", \n" +
                "  description = \"a important task.\"," +
                "  phase = @TaskPhase(name = \"setup\")\n" +
                ")\n" +
                "public class Main {\n" +
                " public Main() {\n" +
                " }\n" +
                "}", TestHelper.ClassPathOption.Option.INHERIT);
        var generator = new JbManifestGenerator(TestHelper.createLog(false).getKey());
        var builder = new StringBuilder();

        generator.createEntryForExtension(classFile, builder);

        assertThat(builder.toString()).isEqualTo("  \"my-task\":\n" +
                "    class-name: Main\n" +
                "    description: a important task.\n" +
                "    phase:\n" +
                "      \"setup\": -1\n" +
                "    config-constructors:\n" +
                "      - {}\n");
    }

    @Test
    void canGenerateSimpleConstructor() throws IOException {
        var classFile = TestHelper.compileJavaClassFile("Main", "" +
                "import jbuild.api.*;\n" +
                "@JbTaskInfo(name = \"my-task\")\n" +
                "public class Main {\n" +
                " public Main(String argument1) {\n" +
                " }\n" +
                "}", TestHelper.ClassPathOption.Option.INHERIT);
        var generator = new JbManifestGenerator(TestHelper.createLog(false).getKey());
        var builder = new StringBuilder();

        generator.createEntryForExtension(classFile, builder);

        assertThat(builder.toString()).isEqualTo("  \"my-task\":\n" +
                "    class-name: Main\n" +
                "    config-constructors:\n" +
                "      - \"argument1\": \"STRING\"\n");
    }

    @Test
    void canGenerateConstructorWithJbConfig() throws IOException {
        var classFile = TestHelper.compileJavaClassFile("Main", "" +
                "import jbuild.api.*;\n" +
                "import jbuild.api.config.JbConfig;\n" +
                "@JbTaskInfo(name = \"my-task\")\n" +
                "public class Main {\n" +
                " public Main(JbConfig jbConfig) {\n" +
                " }\n" +
                "}", TestHelper.ClassPathOption.Option.INHERIT);
        var generator = new JbManifestGenerator(TestHelper.createLog(false).getKey());
        var builder = new StringBuilder();

        generator.createEntryForExtension(classFile, builder);

        assertThat(builder.toString()).isEqualTo("  \"my-task\":\n" +
                "    class-name: Main\n" +
                "    config-constructors:\n" +
                "      - \"jbConfig\": \"JB_CONFIG\"\n");
    }

    @Test
    void canGenerateComplexConstructor() throws IOException {
        var classFile = TestHelper.compileJavaClassFile("foo.bar.MyExtension", "" +
                "package foo.bar;\n" +
                "import jbuild.api.*;\n" +
                "import java.util.List;\n" +
                "@JbTaskInfo(name = \"my-task\")\n" +
                "public class MyExtension {\n" +
                " public MyExtension(boolean b1, int count, List<String> strings, String[] more, float one) {\n" +
                " }\n" +
                "}", TestHelper.ClassPathOption.Option.INHERIT);
        var generator = new JbManifestGenerator(TestHelper.createLog(false).getKey());
        var builder = new StringBuilder();

        generator.createEntryForExtension(classFile, builder);

        assertThat(builder.toString()).isEqualTo("  \"my-task\":\n" +
                "    class-name: foo.bar.MyExtension\n" +
                "    config-constructors:\n" +
                "      - \"b1\": \"BOOLEAN\"\n" +
                "        \"count\": \"INT\"\n" +
                "        \"strings\": \"LIST_OF_STRINGS\"\n" +
                "        \"more\": \"ARRAY_OF_STRINGS\"\n" +
                "        \"one\": \"FLOAT\"\n");
    }

    @Test
    void canGenerateMultipleConstructors() throws IOException {
        var classFile = TestHelper.compileJavaClassFile("foo.bar.MyExtension", "" +
                "package foo.bar;\n" +
                "import jbuild.api.*;\n" +
                "import java.util.List;\n" +
                "@JbTaskInfo(name = \"my-task\")\n" +
                "public class MyExtension {\n" +
                " public MyExtension(boolean b, int c, JBuildLogger log) {}" +
                " public MyExtension(List<String> s, String... more) {}" +
                " public MyExtension() {\n" +
                " }\n" +
                "}", TestHelper.ClassPathOption.Option.INHERIT);
        var generator = new JbManifestGenerator(TestHelper.createLog(false).getKey());
        var builder = new StringBuilder();

        generator.createEntryForExtension(classFile, builder);

        assertThat(builder.toString()).isEqualTo("  \"my-task\":\n" +
                "    class-name: foo.bar.MyExtension\n" +
                "    config-constructors:\n" +
                "      - \"b\": \"BOOLEAN\"\n" +
                "        \"c\": \"INT\"\n" +
                "        \"log\": \"JBUILD_LOGGER\"\n" +
                "      - \"s\": \"LIST_OF_STRINGS\"\n" +
                "        \"more\": \"ARRAY_OF_STRINGS\"\n" +
                "      - {}\n");
    }

    @Test
    void cannotUseUnsupportedTypeInConstructor() throws IOException {
        var classFile = TestHelper.compileJavaClassFile("foo.bar.MyExtension", "" +
                "package foo.bar;\n" +
                "import jbuild.api.*;\n" +
                "import java.util.Set;\n" +
                "@JbTaskInfo(name = \"my-task\")\n" +
                "public class MyExtension {\n" +
                " public MyExtension(Set<String> s) {}" +
                "}", TestHelper.ClassPathOption.Option.INHERIT);
        var generator = new JbManifestGenerator(TestHelper.createLog(false).getKey());
        var builder = new StringBuilder();

        assertThatThrownBy(() -> generator.createEntryForExtension(classFile, builder))
                .isInstanceOfAny(JBuildException.class)
                .hasMessage("jb extension 'foo.bar.MyExtension' could not be created: " +
                        "At class foo.bar.MyExtension, constructor parameter 's' has an unsupported type for " +
                        "jb extension (use one of: jbuild.api.JBuildLogger, jbuild.api.config.JbConfig, " +
                        "java.lang.String, boolean, int, float, java.util.List<java.lang.String>, java.lang.String[])");
    }

}
