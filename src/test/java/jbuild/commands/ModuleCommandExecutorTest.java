package jbuild.commands;

import jbuild.TestSystemProperties;
import jbuild.log.JBuildLog;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleCommandExecutorTest {

    private final JBuildLog log = new JBuildLog(System.out, false);

    @Test
    public void canIdentifySimpleJar() {
        var jar = TestSystemProperties.myClassesJar.getPath();
        var command = new ShowModuleCommand(log);
        var result = new ArrayList<ShowModuleCommand.ModuleOrJar>();
        var errors = new ArrayList<Map.Entry<File, String>>();

        command.check(List.of(jar), result::add, (f, e) -> errors.add(Map.entry(f, e)));

        assertThat(errors).isEmpty();
        assertThat(result).hasSize(1);
        assertThat(result.get(0))
                .isInstanceOf(ShowModuleCommand.SimpleJar.class)
                .isEqualTo(new ShowModuleCommand.SimpleJar(new File(jar), currentMajorJavaVersion()));
    }

    @Test
    public void canIdentifyAutomaticModule() {
        var jar = TestSystemProperties.jbApiJar.getPath();
        var command = new ShowModuleCommand(log);
        var result = new ArrayList<ShowModuleCommand.ModuleOrJar>();
        var errors = new ArrayList<Map.Entry<File, String>>();

        command.check(List.of(jar), result::add, (f, e) -> errors.add(Map.entry(f, e)));

        assertThat(errors).isEmpty();
        assertThat(result).hasSize(1);
        assertThat(result.get(0))
                .isInstanceOf(ShowModuleCommand.AutomaticModule.class)
                .isEqualTo(new ShowModuleCommand.AutomaticModule(
                        new File(jar),
                        "com.athaydes.jbuild.api",
                        currentMajorJavaVersion()));
    }

    private static String currentMajorJavaVersion() {
        var version = System.getProperty("java.version");
        var index = version.indexOf('.');
        return version.substring(0, index);
    }
}
