package jbuild.commands;

import jbuild.errors.JBuildException;
import jbuild.log.JBuildLog;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static java.nio.charset.StandardCharsets.UTF_8;
import static jbuild.TestSystemProperties.myClassesJar;
import static jbuild.TestSystemProperties.otherClassesJar;
import static jbuild.TestSystemProperties.testJarsDir;
import static jbuild.java.TestHelper.jar;
import static jbuild.util.TextUtils.LINE_END;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

public class DoctorCommandExecutorTest {

    @Test
    void shouldFindNoErrorsWhenTwoJarsAreAvailableButEntryPointDoesNotRequireTheOtherJar() {
        withErrorReporting((command) -> {
            var results = command.findValidClasspaths(testJarsDir,
                            false, List.of(myClassesJar), Set.of())
                    .toCompletableFuture()
                    .get();

            assertThat(results.size()).isEqualTo(1);

            var result = results.get(0).map(
                    ok -> ok,
                    err -> fail("could not find classpath permutations", err));

            assertThat(result.errors).isEmpty();
            assertThat(result.successful).isTrue();
            assertThat(Set.copyOf(result.jarSet.getJarByType().values()))
                    .containsExactlyInAnyOrder(jar(myClassesJar));
            assertThat(result.jarSet.getJarByType()).containsAllEntriesOf(Map.of(
                    "LHello;", jar(myClassesJar),
                    "Lfoo/Bar;", jar(myClassesJar)
            ));
        });
    }

    @Test
    void shouldFindNoErrorsWhenTwoJarsAreAvailableAndEntryPointRequiresTheOtherJar() {
        withErrorReporting((command) -> {
            var results = command.findValidClasspaths(testJarsDir,
                            false, List.of(otherClassesJar), Set.of())
                    .toCompletableFuture()
                    .get();

            assertThat(results.size()).isEqualTo(1);

            var result = results.get(0).map(
                    ok -> ok,
                    err -> fail("could not find classpath permutations", err));

            assertThat(result.errors).isEmpty();
            assertThat(result.successful).isTrue();
            assertThat(Set.copyOf(result.jarSet.getJarByType().values()))
                    .containsExactlyInAnyOrder(jar(myClassesJar), jar(otherClassesJar));
            assertThat(result.jarSet.getJarByType()).containsAllEntriesOf(Map.of(
                    "LHello;", jar(myClassesJar),
                    "Lfoo/Bar;", jar(myClassesJar),
                    "Lother/ExtendsBar;", jar(otherClassesJar),
                    "Lother/UsesBar;", jar(otherClassesJar)
            ));
        });
    }

    @Test
    void shouldFindNoErrorsWhenTwoJarsAreAvailableAndEntryPointRequiresTheOtherJar_DuplicateOtherJar()
            throws IOException {
        // copy jars to a temp folder then duplicate otherClassesJar
        var classpathDir = Files.createTempDirectory(DoctorCommandExecutorTest.class.getSimpleName());
        Path myClassesJarCopy = classpathDir.resolve(Paths.get(myClassesJar.getName()));
        Path otherClassesJarCopy = classpathDir.resolve(Paths.get(otherClassesJar.getName()));
        Path otherClassesJarCopy2 = classpathDir.resolve(Paths.get("other-classes-v2.jar"));
        Files.copy(myClassesJar.toPath(), myClassesJarCopy);
        Files.copy(otherClassesJar.toPath(), otherClassesJarCopy);
        Files.copy(otherClassesJar.toPath(), otherClassesJarCopy2);

        withErrorReporting((command) -> {
            var results = command.findValidClasspaths(classpathDir.toFile(),
                            false, List.of(otherClassesJarCopy.toFile()), Set.of())
                    .toCompletableFuture()
                    .get();

            assertThat(results.size()).isEqualTo(1);

            var result = results.get(0).map(
                    ok -> ok,
                    err -> fail("could not find classpath permutations", err));

            assertThat(result.errors).isEmpty();
            assertThat(result.successful).isTrue();
            assertThat(Set.copyOf(result.jarSet.getJarByType().values()))
                    .containsExactlyInAnyOrder(jar(myClassesJarCopy.toFile()), jar(otherClassesJarCopy.toFile()));
            assertThat(result.jarSet.getJarByType()).containsAllEntriesOf(Map.of(
                    "LHello;", jar(myClassesJarCopy.toFile()),
                    "Lfoo/Bar;", jar(myClassesJarCopy.toFile()),
                    "Lother/ExtendsBar;", jar(otherClassesJarCopy.toFile()),
                    "Lother/UsesBar;", jar(otherClassesJarCopy.toFile())
            ));
        });
    }

    @Test
    void shouldErrorWhenEntryPointRequiresMissingJar() throws IOException {
        // copy only otherClassesJar to a temp folder
        var classpathDir = Files.createTempDirectory(DoctorCommandExecutorTest.class.getSimpleName());
        Path otherClassesJarCopy = classpathDir.resolve(Paths.get(otherClassesJar.getName()));
        Files.copy(otherClassesJar.toPath(), otherClassesJarCopy);

        expectError(false, (command) -> {
            var results = command.findValidClasspaths(classpathDir.toFile(),
                            false, List.of(otherClassesJarCopy.toFile()), Set.of())
                    .toCompletableFuture()
                    .get();

            assertThat(results.size()).isEqualTo(1);

            var result = results.get(0).map(
                    ok -> ok,
                    err -> fail("could not find classpath permutations", err));

            assertThat(result.errors).isEmpty();
            assertThat(result.successful).isTrue();
            assertThat(Set.copyOf(result.jarSet.getJarByType().values()))
                    .containsExactlyInAnyOrder(jar(myClassesJar), jar(otherClassesJar));
            assertThat(result.jarSet.getJarByType()).containsAllEntriesOf(Map.of(
                    "LHello;", jar(myClassesJar),
                    "Lfoo/Bar;", jar(myClassesJar),
                    "Lother/ExtendsBar;", jar(otherClassesJar),
                    "Lother/UsesBar;", jar(otherClassesJar)
            ));
        }, (stdout, errorAssert) -> {
            errorAssert.hasRootCauseInstanceOf(JBuildException.class)
                    .getRootCause()
                    .hasMessage("None of the classpaths could provide all types required by the entry-points. " +
                            "See log above for details.");
            var out = stdout.get();
            assertThat(out).contains("Found 4 errors in classpath: " + otherClassesJarCopy + LINE_END);
            assertThat(out).endsWith("  * Type 'Lfoo/Bar;', required by an entry-point, cannot be found in classpath" + LINE_END +
                    "  * Type 'Lfoo/EmptyInterface;', required by an entry-point, cannot be found in classpath" + LINE_END +
                    "  * Type 'Lgenerics/Base;', required by an entry-point, cannot be found in classpath" + LINE_END +
                    "  * Type 'Lgenerics/Generics;', required by an entry-point, cannot be found in classpath" + LINE_END);
        });
    }

    @Test
    void shouldErrorIfEntryPointCannotBeFound() {
        expectError(false, (command) -> {
            command.findValidClasspaths(testJarsDir,
                            false, List.of(new File("does-not-exist.jar")), Set.of())
                    .toCompletableFuture()
                    .get();
        }, (stdout, errorAssert) -> {
            errorAssert.isInstanceOf(JBuildException.class)
                    .hasMessage("Could not find any of the entry points in the input directory.");
            var out = stdout.get();
            assertThat(out).isEmpty();
        });
    }

    private static void withErrorReporting(ThrowingConsumer<DoctorCommandExecutor> test) {
        var stdout = new ByteArrayOutputStream();
        var command = new DoctorCommandExecutor(new JBuildLog(new PrintStream(stdout), true));
        try {
            test.accept(command);
        } catch (Throwable t) {
            System.out.println("STDOUT:\n" + stdout.toString(UTF_8));
            throw t;
        }
    }

    private static void expectError(
            boolean verbose,
            ThrowingConsumer<DoctorCommandExecutor> test,
            BiConsumer<Supplier<String>, AbstractThrowableAssert<?, ? extends Throwable>> assertError) {
        var stdout = new ByteArrayOutputStream();
        var command = new DoctorCommandExecutor(new JBuildLog(new PrintStream(stdout), verbose));
        assertError.accept(() -> stdout.toString(UTF_8), assertThatThrownBy(() -> test.accept(command)));
    }
}
