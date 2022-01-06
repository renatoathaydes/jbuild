package jbuild.commands;

import jbuild.log.JBuildLog;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

import static jbuild.TestSystemProperties.myClassesJar;
import static jbuild.TestSystemProperties.otherClassesJar;
import static jbuild.TestSystemProperties.testJarsDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class DoctorCommandExecutorTest {

    @Test
    void shouldFindNoErrorsInTestJarsDir() throws Exception {
        var command = new DoctorCommandExecutor(new JBuildLog(new PrintStream(new ByteArrayOutputStream()), false));

        var resultsForMyClassesJar = command.findClasspathPermutations(testJarsDir,
                        false, List.of(myClassesJar))
                .toCompletableFuture()
                .get();

        var resultsForOtherClassesJar = command.findClasspathPermutations(testJarsDir,
                        false, List.of(otherClassesJar))
                .toCompletableFuture()
                .get();

        for (var results : List.of(resultsForMyClassesJar, resultsForOtherClassesJar)) {
            assertThat(results.size()).isEqualTo(1);

            var result = results.get(0).map(
                    ok -> ok,
                    err -> fail("could not find classpath permutations", err));

            assertThat(result.errors).isEmpty();
            assertThat(result.successful).isTrue();
            assertThat(result.jarSet.getJarByType()).containsAllEntriesOf(Map.of(
                    "Hello", myClassesJar,
                    "foo.Bar", myClassesJar,
                    "other.ExtendsBar", otherClassesJar,
                    "other.UsesBar", otherClassesJar
            ));
            assertThat(result.jarSet.getJarByType().values()).containsOnly(myClassesJar, otherClassesJar);
        }
    }
}
