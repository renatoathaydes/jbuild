package jbuild.commands;

import jbuild.commands.DoctorCommandExecutor.ClasspathCheckResult;
import jbuild.errors.JBuildException;
import jbuild.java.TestHelper;
import jbuild.log.JBuildLog;
import jbuild.util.Either;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static jbuild.commands.DoctorCommandExecutorRealJarsTest.expectError;
import static jbuild.commands.DoctorCommandExecutorRealJarsTest.withErrorReporting;
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
            var results = command.findValidClasspaths(dir.toFile(),
                            false, List.of(fooJar), Set.of())
                    .toCompletableFuture()
                    .get();
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
            var results = command.findValidClasspaths(dir.toFile(),
                            false, List.of(fooJar), Set.of())
                    .toCompletableFuture()
                    .get();
            assertThat(results).hasSize(1);
            var checkResult = results.get(0);
            assertThat(checkResult.successful).isFalse();
            assertThat(checkResult.getErrors()).isPresent();
            assertThat(checkResult.getErrors().get()).hasSize(1);
            assertThat(checkResult.getErrors().get()).first()
                    .extracting(e -> e.message)
                    .isEqualTo("Method bar.jar!foo.Bar#\"<init>\"()::void, " +
                            "referenced from foo.jar!bar.Foo -> \"<init>\"()::void, " +
                            "cannot be found in the classpath");
        });

        // delete the Bar jar
        assert barJar.delete();

        // again, checking the classpath should fail (Exception because a type requirement is missing)
        expectError(true, (command) -> {
            command.findValidClasspaths(dir.toFile(),
                            false, List.of(fooJar), Set.of())
                    .toCompletableFuture()
                    .get();
        }, (stdout, errorAssert) -> {
            errorAssert.hasRootCauseInstanceOf(JBuildException.class)
                    .getRootCause()
                    .hasMessage("None of the classpaths could provide all types required by the entry-points. " +
                            "See log above for details.");

            var out = stdout.get().lines()
                    .dropWhile(line -> !line.startsWith("Entry-points required types:"))
                    .collect(Collectors.toList());

            assertThat(out).hasSizeGreaterThan(2);
            assertThat(out.get(0)).startsWith("Entry-points required types: ");
            var requiredTypes = Arrays.stream(out.get(0)
                            .substring("Entry-points required types: ".length())
                            .split(",\\s+"))
                    .collect(toSet());
            assertThat(requiredTypes).containsExactlyInAnyOrder("Lfoo/Bar;");

            assertThat(out.get(1)).isEqualTo("Found 1 error in classpath: " + fooJar);

            assertThat(out.subList(2, out.size())).containsExactly(
                    "  * missing references: 'foo.jar!bar.Foo -> foo.Bar'"
            );
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

        // so far, everything should work
        withErrorReporting((command) -> {
            var results = command.findValidClasspaths(dir.toFile(),
                            false, List.of(appJar), Set.of())
                    .toCompletableFuture()
                    .get();
            // notice that unused.jar should not be included in the results
            verifyOneGoodClasspath(results, List.of(appJar, messagesJar, messageUserJar));
        });

        // delete the transitive dependency jar, messages.jar
        assert messagesJar.delete();

        // checking the classpath should fail due to indirect dependency missing
        // (Exception because a type requirement is missing)
        withErrorReporting((command) -> {
            var result = command.findValidClasspaths(dir.toFile(),
                            false, List.of(appJar), Set.of())
                    .toCompletableFuture()
                    .get();
            assertThat(result).hasSize(1);
            // TODO assert the appropriate error message
            assertThat(result.get(0).getErrors()).isPresent()
                    .get().extracting(e -> e.stream().map(it -> it.message).collect(toList()))
                    .isEqualTo(List.of(""));
        });
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
                Files.writeString(src, source);
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
                classpath);

        if (!result.isSuccessful()) {
            verifyToolSuccessful("compile", result.getCompileResult());
            if (result.getJarResult().isPresent()) {
                verifyToolSuccessful("jar", result.getJarResult().get());
            }
        }
    }
}
