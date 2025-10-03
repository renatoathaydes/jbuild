package jbuild.commands;

import jbuild.commands.DoctorCommandExecutor.ClasspathCheckResult;
import jbuild.java.TestHelper;
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
import static jbuild.commands.DoctorCommandExecutorRealJarsTest.withErrorReporting;
import static jbuild.java.tools.Tools.verifyToolSuccessful;
import static org.assertj.core.api.Assertions.assertThat;

public class DoctorCommandExecutorBasicTest {

    @Test
    void canFindHashCodeInAnyType() throws Exception {
        var dir = Files.createTempDirectory(DoctorCommandExecutorBasicTest.class.getName());
        var barJarPath = dir.resolve("bar.jar");
        var barJar = barJarPath.toFile();
        createJar(barJarPath, dir.resolve("src-bar"), Map.of(
                        Paths.get("foo", "Bar.java"),
                        "package foo;\n" +
                                "public class Bar {\n" +
                                "  final int h;\n" +
                                "  public Bar() {\n" +
                                "    h = new Hash().hashCode();\n" +
                                "  }\n" +
                                "}\n" +
                                "class Hash { }\n"),
                "");

        withErrorReporting((command) -> {
            var results = new ArrayList<>(command.findValidClasspaths(dir.toFile(),
                            List.of(barJar), Set.of())
                    .toCompletableFuture()
                    .get());
            verifyOneGoodClasspath(results, List.of(barJar));
        });
    }

    @Test
    void canFindEnumImplicitMethods() throws Exception {
        var dir = Files.createTempDirectory(DoctorCommandExecutorBasicTest.class.getName());
        var barJarPath = dir.resolve("bar.jar");
        var barJar = barJarPath.toFile();
        createJar(barJarPath, dir.resolve("src-bar"), Map.of(
                        Paths.get("foo", "Bar.java"),
                        "package foo;\n" +
                                "public class Bar {\n" +
                                "  final int h;\n" +
                                "  public Bar(E e) {\n" +
                                "    switch (e) {" +
                                "      case A: h = 1; break;\n" +
                                "      default: h = 2; break;\n" +
                                "    }\n" +
                                "  }\n" +
                                "  public static enum E { A, B }" +
                                "}\n" +
                                "\n"),
                "");

        withErrorReporting((command) -> {
            var results = new ArrayList<>(command.findValidClasspaths(dir.toFile(),
                            List.of(barJar), Set.of())
                    .toCompletableFuture()
                    .get());
            verifyOneGoodClasspath(results, List.of(barJar));
        });
    }

    @Test
    void canFindMissingConstructorInClasspath() throws Exception {
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

        // modify the Bar class so that there's no default constructor anymore, which breaks Foo
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

            assertThat(checkResult.getErrors()).isPresent()
                    .get().isEqualTo(NonEmptyCollection.of(new DoctorCommandExecutor.ClassPathInconsistency(
                            "foo.jar!bar.Foo -> bar.jar!foo.Bar::()",
                            "bar.jar!foo.Bar::()",
                            DoctorCommandExecutor.ReferenceTarget.CONSTRUCTOR
                    )));
        });
    }

    @Test
    void canFindMissingFieldInClasspath() throws Exception {
        var dir = Files.createTempDirectory(DoctorCommandExecutorBasicTest.class.getName());
        var barJarPath = dir.resolve("bar.jar");
        var barJar = barJarPath.toFile();
        createJar(barJarPath, dir.resolve("src-bar"), Map.of(
                        Paths.get("foo", "Bar.java"),
                        "package foo;\n" +
                                "public class Bar { public int zort; }"),
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
                                "    this.bar.zort = 1;\n" +
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

        // modify the Bar class so that the zort field does not exist anymore
        assert barJar.delete();
        createJar(barJarPath, dir.resolve("src-bar"), Map.of(
                        Paths.get("foo", "Bar.java"),
                        "package foo;\n" +
                                "public class Bar {\n" +
                                "  public int blah;\n" +
                                "}"),
                "");

        // now, checking the classpath should fail
        withErrorReporting((command) -> {
            var results = new ArrayList<>(command.findValidClasspaths(dir.toFile(), List.of(fooJar), Set.of())
                    .toCompletableFuture().get());
            assertThat(results).hasSize(1);
            var checkResult = results.get(0);
            assertThat(checkResult.successful).isFalse();

            assertThat(checkResult.getErrors()).isPresent()
                    .get().isEqualTo(NonEmptyCollection.of(new DoctorCommandExecutor.ClassPathInconsistency(
                            "foo.jar!bar.Foo -> bar.jar!foo.Bar::zort:int",
                            "bar.jar!foo.Bar::zort:int",
                            DoctorCommandExecutor.ReferenceTarget.FIELD
                    )));
        });
    }

    @Test
    void canFindFieldWithWrongType() throws Exception {
        var dir = Files.createTempDirectory(DoctorCommandExecutorBasicTest.class.getName());
        var barJarPath = dir.resolve("bar.jar");
        var barJar = barJarPath.toFile();
        createJar(barJarPath, dir.resolve("src-bar"), Map.of(
                        Paths.get("foo", "Bar.java"),
                        "package foo;\n" +
                                "public class Bar { public int zort; }"),
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
                                "    this.bar.zort = 1;\n" +
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

        // modify the Bar class so that the zort field has a different type
        assert barJar.delete();
        createJar(barJarPath, dir.resolve("src-bar"), Map.of(
                        Paths.get("foo", "Bar.java"),
                        "package foo;\n" +
                                "public class Bar {\n" +
                                "  public boolean zort;\n" +
                                "}"),
                "");

        // now, checking the classpath should fail
        withErrorReporting((command) -> {
            var results = new ArrayList<>(command.findValidClasspaths(dir.toFile(), List.of(fooJar), Set.of())
                    .toCompletableFuture().get());
            assertThat(results).hasSize(1);
            var checkResult = results.get(0);
            assertThat(checkResult.successful).isFalse();

            assertThat(checkResult.getErrors()).isPresent()
                    .get().isEqualTo(NonEmptyCollection.of(new DoctorCommandExecutor.ClassPathInconsistency(
                            "foo.jar!bar.Foo -> bar.jar!foo.Bar::zort:int",
                            "bar.jar!foo.Bar::zort:int",
                            DoctorCommandExecutor.ReferenceTarget.FIELD
                    )));
        });
    }

    @Test
    void canFindMethodWithWrongType() throws Exception {
        var dir = Files.createTempDirectory(DoctorCommandExecutorBasicTest.class.getName());
        var barJarPath = dir.resolve("bar.jar");
        var barJar = barJarPath.toFile();
        createJar(barJarPath, dir.resolve("src-bar"), Map.of(
                        Paths.get("foo", "Bar.java"),
                        "package foo;\n" +
                                "public class Bar { public String getString() { return \"A\"; } }"),
                "");

        var fooJarPath = dir.resolve("foo.jar");
        var fooJar = fooJarPath.toFile();
        createJar(fooJarPath, dir.resolve("src-foo"), Map.of(
                        Paths.get("bar", "Foo.java"),
                        "package bar;\n" +
                                "import foo.Bar;" +
                                "import java.util.function.Supplier;" +
                                "public class Foo {\n" +
                                "  final Bar bar;" +
                                "  public Foo() {\n" +
                                "    this.bar = new Bar();\n" +
                                "    String s = bar.getString();\n" +
                                "    System.out.println(s);\n" +
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

        // modify the Bar class so that the method uses the wrong type
        assert barJar.delete();
        createJar(barJarPath, dir.resolve("src-bar"), Map.of(
                        Paths.get("foo", "Bar.java"),
                        "package foo;\n" +
                                "public class Bar { public int getString() { return 0; } }"),
                "");

        // now, checking the classpath should fail
        withErrorReporting((command) -> {
            var results = new ArrayList<>(command.findValidClasspaths(dir.toFile(), List.of(fooJar), Set.of())
                    .toCompletableFuture().get());
            assertThat(results).hasSize(1);
            var checkResult = results.get(0);
            assertThat(checkResult.successful).isFalse();

            assertThat(checkResult.getErrors()).isPresent()
                    .get().isEqualTo(NonEmptyCollection.of(new DoctorCommandExecutor.ClassPathInconsistency(
                            "foo.jar!bar.Foo -> bar.jar!foo.Bar::getString():java.lang.String",
                            "bar.jar!foo.Bar::getString():java.lang.String",
                            DoctorCommandExecutor.ReferenceTarget.METHOD
                    )));
        });
    }

    @Test
    void canFindMethodHandleWithWrongType() throws Exception {
        var dir = Files.createTempDirectory(DoctorCommandExecutorBasicTest.class.getName());
        var barJarPath = dir.resolve("bar.jar");
        var barJar = barJarPath.toFile();
        createJar(barJarPath, dir.resolve("src-bar"), Map.of(
                        Paths.get("foo", "Bar.java"),
                        "package foo;\n" +
                                "public class Bar { public String getString() { return \"A\"; } }"),
                "");

        var fooJarPath = dir.resolve("foo.jar");
        var fooJar = fooJarPath.toFile();
        createJar(fooJarPath, dir.resolve("src-foo"), Map.of(
                        Paths.get("bar", "Foo.java"),
                        "package bar;\n" +
                                "import foo.Bar;" +
                                "import java.util.function.Supplier;" +
                                "public class Foo {\n" +
                                "  final Bar bar;" +
                                "  public Foo() {\n" +
                                "    this.bar = new Bar();\n" +
                                "    Supplier<String> s = bar::getString;\n" +
                                "    System.out.println(s.get());\n" +
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

        // modify the Bar class so that the method handle uses the wrong type
        assert barJar.delete();
        createJar(barJarPath, dir.resolve("src-bar"), Map.of(
                        Paths.get("foo", "Bar.java"),
                        "package foo;\n" +
                                "public class Bar { public int getString() { return 0; } }"),
                "");

        // now, checking the classpath should fail
        withErrorReporting((command) -> {
            var results = new ArrayList<>(command.findValidClasspaths(dir.toFile(), List.of(fooJar), Set.of())
                    .toCompletableFuture().get());
            assertThat(results).hasSize(1);
            var checkResult = results.get(0);
            assertThat(checkResult.successful).isFalse();

            assertThat(checkResult.getErrors()).isPresent()
                    .get().isEqualTo(NonEmptyCollection.of(new DoctorCommandExecutor.ClassPathInconsistency(
                            "foo.jar!bar.Foo -> bar.jar!foo.Bar::getString():java.lang.String",
                            "bar.jar!foo.Bar::getString():java.lang.String",
                            DoctorCommandExecutor.ReferenceTarget.METHOD
                    )));
        });
    }

    @Test
    void canFindTransitiveMissingTypeInClasspath() throws Exception {
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

            assertThat(result.get(0).getErrors()).isPresent()
                    .get()
                    .isEqualTo(NonEmptyCollection.of(new DoctorCommandExecutor.ClassPathInconsistency(
                            "app.jar!app.App -> messages-user.jar!user.MessageUser -> messages.Message",
                            "messages.Message",
                            DoctorCommandExecutor.ReferenceTarget.TYPE
                    )));
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
                                  String classpath) throws Exception {
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
                Set.of(),
                Either.right(jar.toString()),
                "",
                "",
                false,
                classpath,
                Either.left(true),
                List.of(),
                null);

        if (!result.isSuccessful()) {
            assertThat(result.getCompileResult()).isPresent();
            verifyToolSuccessful("compile", result.getCompileResult().get());
            if (result.getJarResult().isPresent()) {
                verifyToolSuccessful("jar", result.getJarResult().get());
            }
        }
    }
}
