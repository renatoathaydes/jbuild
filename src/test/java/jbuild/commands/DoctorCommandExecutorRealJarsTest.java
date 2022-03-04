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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;
import static jbuild.TestSystemProperties.myClassesJar;
import static jbuild.TestSystemProperties.otherClassesJar;
import static jbuild.TestSystemProperties.testJarsDir;
import static jbuild.java.TestHelper.jar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DoctorCommandExecutorRealJarsTest {

    @Test
    void shouldFindNoErrorsWhenTwoJarsAreAvailableButEntryPointDoesNotRequireTheOtherJar() {
        withErrorReporting((command) -> {
            var results = command.findValidClasspaths(testJarsDir,
                            false, List.of(myClassesJar), Set.of())
                    .toCompletableFuture()
                    .get();

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
            var results = command.findValidClasspaths(testJarsDir,
                            false, List.of(otherClassesJar), Set.of())
                    .toCompletableFuture()
                    .get();

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

        expectError(true, (command) -> {
            command.findValidClasspaths(classpathDir.toFile(),
                            false, List.of(otherClassesJarCopy.toFile()), Set.of())
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

            assertThat(out).hasSizeGreaterThan(3);
            assertThat(out.get(0)).startsWith("Entry-points required types: ");
            var requiredTypes = Arrays.stream(out.get(0)
                            .substring("Entry-points required types: ".length())
                            .split(",\\s+"))
                    .collect(toSet());
            assertThat(requiredTypes).containsExactlyInAnyOrder(
                    "Lfoo/Bar;",
                    "Lfoo/FunctionalCode;",
                    "Lfoo/SomethingSpecific;",
                    "Lfoo/ExampleLogger;",
                    "Lfoo/Zort;",
                    "Lfoo/MultiInterface;",
                    "Lfoo/Something;",
                    "Lfoo/Fields;",
                    "Lfoo/SomeEnum;",
                    "Lfoo/EmptyInterface;",
                    "Lgenerics/ManyGenerics;",
                    "Lgenerics/Base;",
                    "Lgenerics/BaseA;",
                    "Lgenerics/ComplexType;",
                    "Lgenerics/Generics;",
                    "Lother/UsesEnum$1;"
            );

            assertThat(out.get(1)).isEqualTo("Found 17 errors in classpath: " + otherClassesJarCopy);

            assertThat(out.subList(2, out.size())).containsExactly(
                    "  * missing references: 'other-tests.jar!other.CallsSuperMethod -> foo.Something, foo.SomethingSpecific'",
                    "  * missing references: 'other-tests.jar!other.CallsZortToCreateBar -> foo.Bar, foo.Zort'",
                    "  * missing references: 'other-tests.jar!other.ExtendsBar -> foo.Bar'",
                    "  * missing references: 'other-tests.jar!other.HasSomething -> foo.Something'",
                    "  * missing references: 'other-tests.jar!other.ImplementsEmptyInterface -> foo.EmptyInterface'",
                    "  * missing references: 'other-tests.jar!other.ReadsFieldOfZort -> foo.Bar, foo.Zort'",
                    "  * missing references: 'other-tests.jar!other.UsesArrayOfFunctionalCode -> foo.FunctionalCode'",
                    "  * missing references: 'other-tests.jar!other.UsesBar -> foo.Bar'",
                    "  * missing references: 'other-tests.jar!other.UsesBaseA -> generics.BaseA'",
                    "  * missing references: 'other-tests.jar!other.UsesBaseViaGenerics -> generics.Base'",
                    "  * missing references: 'other-tests.jar!other.UsesComplexType -> foo.Zort, generics.ComplexType, generics.ManyGenerics'",
                    "  * missing references: 'other-tests.jar!other.UsesComplexType$Param -> foo.EmptyInterface, generics.Generics'",
                    "  * missing references: 'other-tests.jar!other.UsesEnum$1 -> foo.SomeEnum'",
                    "  * missing references: 'other-tests.jar!other.UsesFields -> foo.Fields'",
                    "  * missing references: 'other-tests.jar!other.UsesGenerics -> generics.BaseA, generics.Generics'",
                    "  * missing references: 'other-tests.jar!other.UsesMethodHandleFromExampleLogger -> foo.ExampleLogger'",
                    "  * missing references: 'other-tests.jar!other.UsesMultiInterface -> foo.MultiInterface'"
            );
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

    static void withErrorReporting(ThrowingConsumer<DoctorCommandExecutor> test) {
        var stdout = new ByteArrayOutputStream();
        var command = new DoctorCommandExecutor(new JBuildLog(new PrintStream(stdout), true));
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
        var command = new DoctorCommandExecutor(new JBuildLog(new PrintStream(stdout), verbose));
        assertError.accept(() -> stdout.toString(UTF_8),
                assertThatThrownBy(() -> {
                    test.accept(command);
                    System.out.println("NO ERROR WAS THROWN... LOG:\n" + stdout);
                }));
    }
}
