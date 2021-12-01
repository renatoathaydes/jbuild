package jbuild.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OptionsTest {

    @Test
    void canParseOptions() {
        verifyOptions(new Options(),
                List.of(), "out", false, false, false);
        verifyOptions(new Options().parse(new String[]{"foo"}),
                List.of("foo"), "out", false, false, false);
        verifyOptions(new Options().parse(new String[]{"-h"}),
                List.of(), "out", false, true, false);
        verifyOptions(new Options().parse(new String[]{"--help"}),
                List.of(), "out", false, true, false);
        verifyOptions(new Options().parse(new String[]{"-v"}),
                List.of(), "out", false, false, true);
        verifyOptions(new Options().parse(new String[]{"--version"}),
                List.of(), "out", false, false, true);
        verifyOptions(new Options().parse(new String[]{"-V"}),
                List.of(), "out", true, false, false);
        verifyOptions(new Options().parse(new String[]{"-V", "foo"}),
                List.of("foo"), "out", true, false, false);
        verifyOptions(new Options().parse(new String[]{"--verbose", "foo"}),
                List.of("foo"), "out", true, false, false);
        verifyOptions(new Options().parse(new String[]{"--verbose", "foo", "--directory", "target"}),
                List.of("foo"), "target", true, false, false);
        verifyOptions(new Options().parse(new String[]{"--verbose", "foo", "--directory", "target", "bar"}),
                List.of("foo", "bar"), "target", true, false, false);
        verifyOptions(new Options().parse(new String[]{"abc", "def", "ghi", "jkl", "mno"}),
                List.of("abc", "def", "ghi", "jkl", "mno"), "out", false, false, false);
    }

    private void verifyOptions(Options options, List<String> artifacts,
                               String outDir,
                               boolean verbose,
                               boolean help,
                               boolean version) {
        assertEquals(artifacts, options.artifacts);
        assertEquals(outDir, options.outDir);
        assertEquals(verbose, options.verbose);
        assertEquals(help, options.help);
        assertEquals(version, options.version);
    }
}
