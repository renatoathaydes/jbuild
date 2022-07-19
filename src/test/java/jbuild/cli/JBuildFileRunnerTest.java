package jbuild.cli;

import jbuild.log.JBuildLog;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JBuildFileRunnerTest {

    @Test
    void canParseHelp() throws Exception {
        var options = run(List.of("--help"));
        assertThat(options).hasSize(1);
        assertOptions(options.get(0), false, true, false, "",
                List.of(), List.of(), List.of());
    }

    @Test
    void canParseVerboseAndCommandSameLine() throws Exception {
        var options = run(List.of("--verbose foo"));
        assertThat(options).hasSize(1);
        assertOptions(options.get(0), true, false, false, "foo",
                List.of(), List.of(), List.of());
    }

    @Test
    void canParseVerboseAndCommandSeparateLines() throws Exception {
        var options = run(List.of("--verbose", " foo"));
        assertThat(options).hasSize(1);
        assertOptions(options.get(0), true, false, false, "foo",
                List.of(), List.of(), List.of());
    }

    @Test
    void canParseMultiRootArgsAndCommandWithArgsMultiLine() throws Exception {
        var options = run(List.of("--verbose", "  -r r1",
                "  --repository r2", " go ", "  --bar", " other", "   -- zort"));
        assertThat(options).hasSize(1);
        assertOptions(options.get(0), true, false, false, "go",
                List.of("r1", "r2"), List.of("--bar", "other"), List.of("zort"));
    }

    @Test
    void canParseMultipleCommands() throws Exception {
        var options = run(List.of("--verbose", "-h",
                "-r repo", " install -d foo", "  dep1", "  dep2", "", "doctor", " foo", "compile -d foo"));
        assertThat(options).hasSize(5);
        assertOptions(options.get(0), true, false, false, "",
                List.of(), List.of(), List.of());
        assertOptions(options.get(1), false, true, false, "",
                List.of(), List.of(), List.of());
        assertOptions(options.get(2), false, false, false, "install",
                List.of("repo"), List.of("-d", "foo", "dep1", "dep2"), List.of());
        assertOptions(options.get(3), false, false, false, "doctor",
                List.of(), List.of("foo"), List.of());
        assertOptions(options.get(4), false, false, false, "compile",
                List.of(), List.of("-d", "foo"), List.of());
    }

    private static void assertOptions(Options options,
                                      boolean verbose,
                                      boolean help,
                                      boolean version,
                                      String command,
                                      List<String> repositories,
                                      List<String> commandArgs,
                                      List<String> applicationArgs) {
        assertThat(options.verbose).isEqualTo(verbose);
        assertThat(options.version).isEqualTo(version);
        assertThat(options.help).isEqualTo(help);
        assertThat(options.command).isEqualTo(command);
        assertThat(options.repositories).isEqualTo(repositories);
        assertThat(options.commandArgs).isEqualTo(commandArgs);
        assertThat(options.applicationArgs).isEqualTo(applicationArgs);
    }

    private static List<Options> run(List<String> fileContents, String... args) throws Exception {
        var options = new ArrayList<Options>();
        var runner = new JBuildFileRunner(log(), options::add, (file, charset) -> fileContents);
        runner.run(Options.parse(args));
        return options;
    }

    private static JBuildLog log() {
        var bytesOut = new ByteArrayOutputStream();
        var out = new PrintStream(bytesOut);
        return new JBuildLog(out, false);
    }
}
