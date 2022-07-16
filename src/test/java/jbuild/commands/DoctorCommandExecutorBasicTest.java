package jbuild.commands;

import jbuild.commands.DoctorCommandExecutor.ClassPathInconsistency;
import jbuild.commands.DoctorCommandExecutor.ClasspathCheckResult;
import jbuild.java.TestHelper;
import jbuild.java.code.Code;
import jbuild.log.JBuildLog;
import jbuild.util.Either;
import jbuild.util.NonEmptyCollection;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static jbuild.commands.DoctorCommandExecutorRealJarsTest.withErrorReporting;
import static jbuild.java.code.Code.Method.Instruction.invokespecial;
import static jbuild.java.tools.Tools.verifyToolSuccessful;
import static org.assertj.core.api.Assertions.assertThat;

public class DoctorCommandExecutorBasicTest {

    @Test
    void canFindMissingTypeInClasspath() throws IOException {
        var dir = Files.createTempDirectory(DoctorCommandExecutorBasicTest.class.getName());
        var barJarPath = dir.resolve("bar.jar");
        var barJar = barJarPath.toFile();
        createJar(barJarPath, dir.resolve("src-bar"), Map.of(
                        Paths.get("foo", "Bar.java"),
                        "package foo;\n" +
                                "public class Bar {}"),
                "");

        var fooJarPath = dir.resolve("foo.jar");
        var fooJar = fooJarPath.toFile();
        createJar(fooJarPath, dir.resolve("src-foo"), Map.of(
                        Paths.get("bar", "Foo.java"),
                        "package bar;\n" +
                                "import foo.Bar;" +
                                "public class Foo {\n" +
                                "  final Bar bar;" +
                                "  public Foo() {\n" +
                                "    this.bar = new Bar();\n" +
                                "  }\n" +
                                "}"),
                barJar.getAbsolutePath());

        // so far, everything should work
        withErrorReporting((command) -> {
            var results = new ArrayList<>(command.findValidClasspaths(dir.toFile(),
                            List.of(fooJar), Set.of())
                    .toCompletableFuture()
                    .get());
            verifyOneGoodClasspath(results, List.of(fooJar, barJar));
        });

        // modify the Bar class so that it can't be used by Foo anymore
        assert barJar.delete();
        createJar(barJarPath, dir.resolve("src-bar"), Map.of(
                        Paths.get("foo", "Bar.java"),
                        "package foo;\n" +
                                "public class Bar {\n" +
                                "  public Bar(String s) {}\n" +
                                "}"),
                "");

        // now, checking the classpath should fail
        withErrorReporting((command) -> {
            var results = new ArrayList<>(command.findValidClasspaths(dir.toFile(), List.of(fooJar), Set.of())
                    .toCompletableFuture().get());
            assertThat(results).hasSize(1);
            var checkResult = results.get(0);
            assertThat(checkResult.successful).isFalse();

            assertThat(errorString(checkResult.getErrors().orElse(null)))
                    .isEqualTo(
                            errorString(NonEmptyCollection.of(
                                    new ClassPathInconsistency(
                                            "foo.jar!bar.Foo -> \"<init>\"()::void",
                                            new Code.Method("Lfoo/Bar;", "\"<init>\"", "()V", invokespecial),
                                            new File("foo.jar"),
                                            new File("bar.jar")
                                    ))));
        });

        // delete the Bar jar
        assert barJar.delete();

        // type references are now missing
        withErrorReporting((command) -> {
            var results = new ArrayList<>(command.findValidClasspaths(dir.toFile(),
                            List.of(fooJar), Set.of())
                    .toCompletableFuture()
                    .get());

            assertThat(results).hasSize(1);
            var checkResult = results.get(0);
            assertThat(checkResult.successful).isFalse();
            assertThat(errorString(checkResult.getErrors().orElse(null)))
                    .isEqualTo(errorString(NonEmptyCollection.of(List.of(
                            new ClassPathInconsistency(
                                    "foo.jar!bar.Foo -> bar::foo.Bar",
                                    new Code.Type("Lfoo/Bar;"),
                                    new File("foo.jar"),
                                    null
                            ), new ClassPathInconsistency(
                                    "foo.jar!bar.Foo -> \"<init>\"()::void",
                                    new Code.Type("Lfoo/Bar;"),
                                    new File("foo.jar"),
                                    null
                            ), new ClassPathInconsistency(
                                    "foo.jar!bar.Foo -> \"<init>\"()::void -> foo.Bar#\"<init>\"()::void",
                                    new Code.Type("Lfoo/Bar;"),
                                    new File("foo.jar"),
                                    null
                            )))));
        });
    }

    @Test
    void canFindTransitiveIncompatibility() throws IOException {
        var dir = Files.createTempDirectory(DoctorCommandExecutorBasicTest.class.getName());

        // this jar is used by messages.jar but won't be used by the final application!
        var unusedJarPath = dir.resolve("unused.jar");
        var unusedJar = unusedJarPath.toFile();
        createJar(unusedJarPath, dir.resolve("unused"), Map.of(
                        Paths.get("unused", "Unused.java"),
                        "package unused;\n" +
                                "public class Unused {}"),
                "");

        var messagesJarPath = dir.resolve("messages.jar");
        var messagesJar = messagesJarPath.toFile();
        createJar(messagesJarPath, dir.resolve("messages"), Map.of(
                        Paths.get("messages", "Message.java"),
                        "package messages;\n" +
                                "public class Message {\n" +
                                "  public String get() { return \"a message\"; }\n" +
                                "}",
                        Paths.get("messages", "Other.java"),
                        "package messages;\n" +
                                "import unused.Unused;\n" +
                                "public class Other {\n" +
                                "  public Unused get() { return new Unused(); }\n" +
                                "}"),
                unusedJar.getAbsolutePath());

        var messageUserJarPath = dir.resolve("messages-user.jar");
        var messageUserJar = messageUserJarPath.toFile();
        createJar(messageUserJarPath, dir.resolve("messages-user"), Map.of(
                        Paths.get("user", "MessageUser.java"),
                        "package user;\n" +
                                "import messages.Message;" +
                                "public class MessageUser {\n" +
                                "  final Message message;" +
                                "  public MessageUser() {\n" +
                                "    this.message = new Message();\n" +
                                "  }\n" +
                                "  public String getMessage() {\n" +
                                "    return message.get();\n" +
                                "  }\n" +
                                "}"),
                messagesJar.getAbsolutePath());

        var appJarPath = dir.resolve("app.jar");
        var appJar = appJarPath.toFile();
        createJar(appJarPath, dir.resolve("app"), Map.of(
                        Paths.get("app", "App.java"),
                        "package app;\n" +
                                "import user.MessageUser;\n" +
                                "public class App {\n" +
                                "  public static void main(String... args) {\n" +
                                "    System.out.println(new MessageUser().getMessage());\n" +
                                "  }\n" +
                                "}"),
                messageUserJar.getAbsolutePath());

        // unused.jar:
        //    - unused.Unused
        // messages.jar:
        //    - messages.Message
        //    - messages.Other (imports unused.Unused)
        // messages-user.jar
        //    - user.MessageUser (imports messages.Message)
        // app.jar
        //    - app.App (imports user.MessageUser)

        // so far, everything should work
        withErrorReporting((command) -> {
            var results = new ArrayList<>(command.findValidClasspaths(dir.toFile(),
                            List.of(appJar), Set.of())
                    .toCompletableFuture()
                    .get());
            // notice that unused.jar should not be included in the results
            verifyOneGoodClasspath(results, List.of(appJar, messagesJar, messageUserJar));
        });

        // delete the transitive dependency jar, messages.jar
        assert messagesJar.delete();

        // checking the classpath should fail due to indirect dependency missing
        // (Exception because a type requirement is missing)
        withErrorReporting((command) -> {
            var result = new ArrayList<>(command.findValidClasspaths(dir.toFile(),
                            List.of(appJar), Set.of())
                    .toCompletableFuture()
                    .get());
            assertThat(result).hasSize(1);

            var main = "app.jar!app.App -> main(java.lang.String[])::void -> ";

            assertThat(result.get(0).getErrors()).isPresent()
                    .get().extracting(e -> e.stream().map(it -> it.referenceChain).collect(toSet()))
                    .isEqualTo(Set.of(
                            main + "user.MessageUser#\"<init>\"()::void",
                            main + "user.MessageUser#getMessage()::java.lang.String " +
                                    "-> messages.Message#get()::java.lang.String",
                            main + "user.MessageUser#\"<init>\"()::void " +
                                    "-> messages.Message#\"<init>\"()::void"
                    ));
        });
    }

    private static String errorString(
            NonEmptyCollection<ClassPathInconsistency> inconsistencies) {
        if (inconsistencies == null) return "Optional.empty()";
        var result = "";
        for (var error : inconsistencies) {
            result = "\n" + error.referenceChain +
                    "\nJarFrom=" + (error.jarFrom == null ? "null" : error.jarFrom.getName()) +
                    "\nTo=" + error.to +
                    "\nJarTo=" + (error.jarTo == null ? "null" : error.jarTo.getName());
        }
        return result;
    }

    private static void verifyOneGoodClasspath(List<ClasspathCheckResult> results,
                                               List<File> jars) {
        assertThat(results.size()).isEqualTo(1);

        var result = results.get(0);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.successful).withFailMessage(result::toString).isTrue();
        assertThat(Set.copyOf(result.jarSet.getJarByType().values()))
                .containsExactlyInAnyOrderElementsOf(jars.stream()
                        .map(TestHelper::jar)
                        .collect(toList()));
    }

    private static void createJar(Path jar,
                                  Path rootDir,
                                  Map<Path, String> sourceByPath,
                                  String classpath) {
        sourceByPath.forEach((path, source) -> {
            var src = rootDir.resolve(path);
            src.toFile().getParentFile().mkdirs();
            try {
                Files.writeString(src, source, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        var bytesOut = new ByteArrayOutputStream();
        var out = new PrintStream(bytesOut);
        var log = new JBuildLog(out, false);
        var command = new CompileCommandExecutor(log);

        var result = command.compile(
                Set.of(rootDir.toString()),
                Either.right(jar.toString()),
                "",
                classpath);

        if (!result.isSuccessful()) {
            verifyToolSuccessful("compile", result.getCompileResult());
            if (result.getJarResult().isPresent()) {
                verifyToolSuccessful("jar", result.getJarResult().get());
            }
        }
    }
}
