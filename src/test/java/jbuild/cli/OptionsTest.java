package jbuild.cli;

import jbuild.errors.JBuildException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OptionsTest {

    @Test
    void canParseMainOptions() {
        verifyOptions(Options.parse(new String[]{}),
                "", List.of(), false, false, false);
        verifyOptions(Options.parse(new String[]{"foo"}),
                "foo", List.of(), false, false, false);
        verifyOptions(Options.parse(new String[]{"-h"}),
                "", List.of(), false, true, false);
        verifyOptions(Options.parse(new String[]{"--help"}),
                "", List.of(), false, true, false);
        verifyOptions(Options.parse(new String[]{"-v"}),
                "", List.of(), false, false, true);
        verifyOptions(Options.parse(new String[]{"--version"}),
                "", List.of(), false, false, true);
        verifyOptions(Options.parse(new String[]{"--version", "-v"}),
                "", List.of(), false, false, true);
        verifyOptions(Options.parse(new String[]{"-V"}),
                "", List.of(), true, false, false);
        verifyOptions(Options.parse(new String[]{"-V", "-V"}),
                "", List.of(), true, false, false);
        verifyOptions(Options.parse(new String[]{"-V", "foo"}),
                "foo", List.of(), true, false, false);
        verifyOptions(Options.parse(new String[]{"--verbose", "foo"}),
                "foo", List.of(), true, false, false);
        verifyOptions(Options.parse(new String[]{"--verbose", "foo", "--directory", "target"}),
                "foo", List.of("--directory", "target"), true, false, false);
        verifyOptions(Options.parse(new String[]{"--verbose", "foo", "--directory", "target", "bar"}),
                "foo", List.of("--directory", "target", "bar"), true, false, false);
        verifyOptions(Options.parse(new String[]{"abc", "def", "ghi", "jkl", "mno"}),
                "abc", List.of("def", "ghi", "jkl", "mno"), false, false, false);
    }

    @Test
    void mustNotRecognizeUnknownOption() {
        assertThatThrownBy(() -> Options.parse(new String[]{"-f"}))
                .isInstanceOf(JBuildException.class)
                .hasMessage("invalid root option: -f\n" +
                        "Run jbuild --help for usage.");

        assertThatThrownBy(() -> Options.parse(new String[]{"-v", "--nothing"}))
                .isInstanceOf(JBuildException.class)
                .hasMessage("invalid root option: --nothing\n" +
                        "Run jbuild --help for usage.");
    }

    private void verifyOptions(Options options,
                               String command,
                               List<String> commandArgs,
                               boolean verbose,
                               boolean help,
                               boolean version) {
        assertEquals(command, options.command);
        assertEquals(commandArgs, options.commandArgs);
        assertEquals(verbose, options.verbose);
        assertEquals(help, options.help);
        assertEquals(version, options.version);
    }
}
