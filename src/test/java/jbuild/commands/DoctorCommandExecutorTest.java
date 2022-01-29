package jbuild.commands;

import jbuild.log.JBuildLog;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static jbuild.TestSystemProperties.myClassesJar;
import static jbuild.TestSystemProperties.otherClassesJar;
import static jbuild.TestSystemProperties.testJarsDir;
import static jbuild.java.TestHelper.jar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class DoctorCommandExecutorTest {

    @Test
    void shouldFindNoErrorsWhenTwoJarsAreAvailableButEntryPointDoesNotRequireTheOtherJar() throws Exception {
        var stdout = new ByteArrayOutputStream();
        var command = new DoctorCommandExecutor(new JBuildLog(new PrintStream(stdout), true));

        try {
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
        } catch (Throwable e) {
            System.out.println("STDOUT:\n" + stdout.toString(UTF_8));
            throw e;
        }
    }

    @Test
    void shouldFindNoErrorsWhenTwoJarsAreAvailableAndEntryPointRequiresTheOtherJar() throws Exception {
        var stdout = new ByteArrayOutputStream();
        var command = new DoctorCommandExecutor(new JBuildLog(new PrintStream(stdout), true));

        try {
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
        } catch (Throwable e) {
            System.out.println("STDOUT:\n" + stdout.toString(UTF_8));
            throw e;
        }
    }
}
