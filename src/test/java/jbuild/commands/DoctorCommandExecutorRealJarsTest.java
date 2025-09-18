package jbuild.commands;

import jbuild.api.JBuildException;
import jbuild.log.JBuildLog;
import jbuild.util.NonEmptyCollection;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static jbuild.TestSystemProperties.myClassesJar;
import static jbuild.TestSystemProperties.otherClassesJar;
import static jbuild.TestSystemProperties.testJarsDir;
import static jbuild.java.TestHelper.jar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Disabled("doctor command requires re-implementation after javap parser was removed")
public class DoctorCommandExecutorRealJarsTest {

    @Test
    void shouldFindNoErrorsWhenTwoJarsAreAvailableButEntryPointDoesNotRequireTheOtherJar() {
        withErrorReporting((command) -> {
            var results = new ArrayList<>(command.findValidClasspaths(testJarsDir,
                            List.of(myClassesJar), Set.of())
                    .toCompletableFuture()
                    .get());

            assertThat(results.size()).isEqualTo(1);

            var result = results.get(0);

            assertThat(result.getErrors()).isEmpty();
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
            var results = new ArrayList<>(command.findValidClasspaths(testJarsDir,
                            List.of(otherClassesJar), Set.of())
                    .toCompletableFuture()
                    .get());

            assertThat(results.size()).isEqualTo(1);

            var result = results.get(0);

            assertThat(result.getErrors()).isEmpty();
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
        var classpathDir = Files.createTempDirectory(DoctorCommandExecutorRealJarsTest.class.getSimpleName());
        Path myClassesJarCopy = classpathDir.resolve(Paths.get(myClassesJar.getName()));
        Path otherClassesJarCopy = classpathDir.resolve(Paths.get(otherClassesJar.getName()));
        Path otherClassesJarCopy2 = classpathDir.resolve(Paths.get("other-tests-v2.jar"));
        Files.copy(myClassesJar.toPath(), myClassesJarCopy);
        Files.copy(otherClassesJar.toPath(), otherClassesJarCopy);
        Files.copy(otherClassesJar.toPath(), otherClassesJarCopy2);

        withErrorReporting((command) -> {
            var results = new ArrayList<>(command.findValidClasspaths(classpathDir.toFile(),
                            List.of(otherClassesJarCopy.toFile()), Set.of())
                    .toCompletableFuture()
                    .get());

            assertThat(results.size()).isEqualTo(1);

            var result = results.get(0);

            assertThat(result.getErrors()).isEmpty();
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
        var classpathDir = Files.createTempDirectory(DoctorCommandExecutorRealJarsTest.class.getSimpleName());
        Path otherClassesJarCopy = classpathDir.resolve(Paths.get(otherClassesJar.getName()));
        Files.copy(otherClassesJar.toPath(), otherClassesJarCopy);

        withErrorReporting((command) -> {
            var results = command.findValidClasspaths(classpathDir.toFile(),
                            List.of(otherClassesJarCopy.toFile()), Set.of())
                    .toCompletableFuture()
                    .get();

            assertThat(results.size()).isEqualTo(1);

            var result = results.iterator().next();

            var jarFromSet = result.getErrors().stream()
                    .flatMap(NonEmptyCollection::stream)
                    .map(error -> error.jarFrom.getName())
                    .collect(Collectors.toSet());

            assertThat(jarFromSet).containsExactly(otherClassesJar.getName());

            var jarToSet = result.getErrors().stream()
                    .flatMap(NonEmptyCollection::stream)
                    .map(error -> error.jarTo == null ? "null" : error.jarTo.getName())
                    .collect(Collectors.toSet());

            // the jarTo must be null because there's no way of knowing where the missing types should come from
            assertThat(jarToSet).containsExactly("null");

            var codeToSet = result.getErrors().stream()
                    .flatMap(NonEmptyCollection::stream)
                    .map(error -> error.to)
                    .collect(Collectors.toSet());

            assertThat(codeToSet).containsExactlyInAnyOrder(
                    "Lfoo/Bar;",
                    "Lfoo/FunctionalCode;",
                    "Lfoo/SomethingSpecific;",
                    "Lgenerics/Base;",
                    "Lfoo/ExampleLogger;",
                    "Lfoo/Zort;",
                    "Lgenerics/BaseA;",
                    "Lfoo/MultiInterface;",
                    "Lfoo/Something;",
                    "Lgenerics/ComplexType;",
                    "Lfoo/Fields;",
                    "Lfoo/SomeEnum;",
                    "Lgenerics/Generics;",
                    "Lfoo/EmptyInterface;");

            assertThat(result.successful).isFalse();
        });
    }

    @Test
    void shouldErrorIfEntryPointCannotBeFound() {
        expectError(false, (command) -> {
            command.findValidClasspaths(testJarsDir,
                            List.of(new File("does-not-exist.jar")), Set.of())
                    .toCompletableFuture()
                    .get();
        }, (stdout, errorAssert) -> {
            errorAssert.isInstanceOf(JBuildException.class)
                    .hasMessage("Could not find any of the entry points in the input directory.");
            var out = stdout.get();
            assertThat(out).isEmpty();
        });
    }

    static void withErrorReporting(ThrowingConsumer<DoctorCommandExecutor> test) {
        var stdout = new ByteArrayOutputStream();
        var command = new DoctorCommandExecutor(new JBuildLog(new PrintStream(stdout, true, ISO_8859_1), true));
        try {
            test.accept(command);
        } catch (Throwable t) {
            System.out.println("STDOUT:\n" + stdout.toString(UTF_8));
            throw t;
        }
    }

    static void expectError(
            boolean verbose,
            ThrowingConsumer<DoctorCommandExecutor> test,
            BiConsumer<Supplier<String>, AbstractThrowableAssert<?, ? extends Throwable>> assertError) {
        var stdout = new ByteArrayOutputStream();
        var command = new DoctorCommandExecutor(new JBuildLog(new PrintStream(stdout, true, UTF_8), verbose));
        assertError.accept(() -> stdout.toString(UTF_8),
                assertThatThrownBy(() -> {
                    test.accept(command);
                    System.out.println("NO ERROR WAS THROWN... LOG:\n" + stdout);
                }));
    }
}
