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

public class DoctorCommandExecutorTest {

    @Test
    void shouldFindNoErrorsInTestJarsDir() {
        var command = new DoctorCommandExecutor(new JBuildLog(new PrintStream(new ByteArrayOutputStream()), false));

        var resultsForMyClassesJar = command.findClasspathPermutations(testJarsDir,
                false, List.of(myClassesJar));

        var resultsForOtherClassesJar = command.findClasspathPermutations(testJarsDir,
                false, List.of(otherClassesJar));

        for (var results : List.of(resultsForMyClassesJar, resultsForOtherClassesJar)) {
            assertThat(results.size()).isEqualTo(1);
            assertThat(results.get(0).errors).isEmpty();
            assertThat(results.get(0).successful).isTrue();
            assertThat(results.get(0).jarByType).containsAllEntriesOf(Map.of(
                    "LHello;", myClassesJar,
                    "Lfoo/Bar;", myClassesJar,
                    "Lother/ExtendsBar;", otherClassesJar,
                    "Lother/UsesBar;", otherClassesJar
            ));
            assertThat(results.get(0).jarByType.values()).containsOnly(myClassesJar, otherClassesJar);
        }
    }
}
