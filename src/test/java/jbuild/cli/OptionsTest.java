package jbuild.cli;

import jbuild.errors.JBuildException;
import jbuild.util.Either;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Set;

import static jbuild.util.TextUtils.LINE_END;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OptionsTest {

    @Test
    void canParseMainOptions() {
        verifyOptions(Options.parse(new String[]{}),
                "", List.of(), List.of(), false, false, false);
        verifyOptions(Options.parse(new String[]{"foo"}),
                "foo", List.of(), List.of(), false, false, false);
        verifyOptions(Options.parse(new String[]{"-h"}),
                "", List.of(), List.of(), false, true, false);
        verifyOptions(Options.parse(new String[]{"--help"}),
                "", List.of(), List.of(), false, true, false);
        verifyOptions(Options.parse(new String[]{"-v"}),
                "", List.of(), List.of(), false, false, true);
        verifyOptions(Options.parse(new String[]{"--version"}),
                "", List.of(), List.of(), false, false, true);
        verifyOptions(Options.parse(new String[]{"--version", "-v"}),
                "", List.of(), List.of(), false, false, true);
        verifyOptions(Options.parse(new String[]{"-V"}),
                "", List.of(), List.of(), true, false, false);
        verifyOptions(Options.parse(new String[]{"-V", "-V"}),
                "", List.of(), List.of(), true, false, false);
        verifyOptions(Options.parse(new String[]{"-V", "foo"}),
                "foo", List.of(), List.of(), true, false, false);
        verifyOptions(Options.parse(new String[]{"--verbose", "foo"}),
                "foo", List.of(), List.of(), true, false, false);
        verifyOptions(Options.parse(new String[]{"--verbose", "foo", "--directory", "target"}),
                "foo", List.of("--directory", "target"), List.of(), true, false, false);
        verifyOptions(Options.parse(new String[]{"--verbose", "foo", "--directory", "target", "bar"}),
                "foo", List.of("--directory", "target", "bar"), List.of(), true, false, false);
        verifyOptions(Options.parse(new String[]{"abc", "def", "ghi", "jkl", "--", "mno", "-p"}),
                "abc", List.of("def", "ghi", "jkl"), List.of("mno", "-p"), false, false, false);
    }

    @Test
    void mustNotRecognizeUnknownOption() {
        assertThatThrownBy(() -> Options.parse(new String[]{"-f"}))
                .isInstanceOf(JBuildException.class)
                .hasMessage("invalid root option: -f" + LINE_END +
                        "Run jbuild --help for usage.");

        assertThatThrownBy(() -> Options.parse(new String[]{"-v", "--nothing"}))
                .isInstanceOf(JBuildException.class)
                .hasMessage("invalid root option: --nothing" + LINE_END +
                        "Run jbuild --help for usage.");
    }

    @Test
    void parseCompileOptions() {
        var p = File.pathSeparatorChar;
        verifyCompileOptions(CompileOptions.parse(
                        Options.parse(new String[]{"compile"}).commandArgs),
                "java-libs", Set.of(), Either.right(""), "");

        verifyCompileOptions(CompileOptions.parse(
                        Options.parse(new String[]{"compile", "--main-class", "a.b.C", "-d", "out"}).commandArgs),
                "java-libs", Set.of(), Either.left("out"), "a.b.C");

        verifyCompileOptions(CompileOptions.parse(
                        Options.parse(new String[]{"compile", "-m", "a.b.C", "--jar", "lib.jar", "--classpath", "foo"}).commandArgs),
                "foo", Set.of(), Either.right("lib.jar"), "a.b.C");

        verifyCompileOptions(CompileOptions.parse(
                        Options.parse(new String[]{"compile", "dir", "-cp", "a:b;c", "-cp", "d"}).commandArgs),
                "a" + p + "b" + p + "c" + p + "d", Set.of("dir"), Either.right(""), "");
    }

    private void verifyOptions(Options options,
                               String command,
                               List<String> commandArgs,
                               List<String> applicationArgs,
                               boolean verbose,
                               boolean help,
                               boolean version) {
        assertEquals(command, options.command);
        assertEquals(commandArgs, options.commandArgs);
        assertEquals(applicationArgs, options.applicationArgs);
        assertEquals(verbose, options.verbose);
        assertEquals(help, options.help);
        assertEquals(version, options.version);
    }

    private void verifyCompileOptions(CompileOptions options,
                                      String classpath,
                                      Set<String> inputDirs,
                                      Either<String, String> outputDirOrJar,
                                      String mainClass) {
        assertEquals(classpath, options.classpath);
        assertEquals(inputDirs, options.inputDirectories);
        assertEquals(outputDirOrJar, options.outputDirOrJar);
        assertEquals(mainClass, options.mainClass);
    }
}
