package jbuild.cli;

import jbuild.api.JBuildException;
import jbuild.maven.Scope;
import jbuild.util.Either;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.toSet;
import static jbuild.util.CollectionUtils.mapValues;
import static jbuild.util.TextUtils.LINE_END;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.MapAssert.assertThatMap;
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
                .hasMessage("invalid root option: -f." + LINE_END +
                        "Run jbuild --help for usage.");

        assertThatThrownBy(() -> Options.parse(new String[]{"-v", "--nothing"}))
                .isInstanceOf(JBuildException.class)
                .hasMessage("invalid root option: --nothing." + LINE_END +
                        "Run jbuild --help for usage.");
    }

    @Test
    void parseCompileOptions() {
        Either<Boolean, String> defaultManifest = Either.left(true);
        var p = File.pathSeparatorChar;
        verifyCompileOptions(CompileOptions.parse(
                        Options.parse(new String[]{"compile"}).commandArgs, false),
                "java-libs", "", Set.of(), Set.of(), Either.right(""), "", defaultManifest, "");

        verifyCompileOptions(CompileOptions.parse(
                        Options.parse(new String[]{"compile", "--main-class", "a.b.C", "-d", "out"}).commandArgs, false),
                "java-libs", "", Set.of(), Set.of(), Either.left("out"), "a.b.C", defaultManifest, "");

        verifyCompileOptions(CompileOptions.parse(
                        Options.parse(new String[]{"compile", "-m", "a.b.C", "--jar", "lib.jar", "--classpath", "foo"}).commandArgs, false),
                "foo", "", Set.of(), Set.of(), Either.right("lib.jar"), "a.b.C", defaultManifest, "");

        verifyCompileOptions(CompileOptions.parse(
                        Options.parse(new String[]{"compile", "dir", "-cp", "a:b;c", "-cp", "d"}).commandArgs, false),
                "a" + p + "b" + p + "c" + p + "d", "", Set.of("dir"), Set.of(), Either.right(""), "", defaultManifest, "");

        verifyCompileOptions(CompileOptions.parse(
                        Options.parse(new String[]{"compile", "--resources", "res", "dir", "-cp", "d", "-r", "files"}).commandArgs, false),
                "d", "", Set.of("dir"), Set.of("res", "files"), Either.right(""), "", defaultManifest, "");

        verifyCompileOptions(CompileOptions.parse(
                        Options.parse(new String[]{"compile", "--manifest", "-"}).commandArgs, false),
                "java-libs", "", Set.of(), Set.of(), Either.right(""), "", Either.left(false), "");

        verifyCompileOptions(CompileOptions.parse(
                        Options.parse(new String[]{"compile", "-mf", "MANIFEST.txt"}).commandArgs, false),
                "java-libs", "", Set.of(), Set.of(), Either.right(""), "", Either.right("MANIFEST.txt"), "");

        // -q -V compile -m example.Main src -g build/compile-libs/groovy-4.0.20.jar -mp build/compile-libs/groovy-4.0.20.jar
        verifyCompileOptions(CompileOptions.parse(
                        Options.parse(new String[]{"compile", "-m", "example.Main", "src",
                                "-g", "build/libs/groovy1.jar", "-mp", "build/libs/groovy2.jar"}
                        ).commandArgs, false),
                "java-libs", "build/libs/groovy2.jar", Set.of("src"), Set.of(), Either.right(""), "example.Main", Either.left(true),
                "build/libs/groovy1.jar");
    }

    @Test
    void parseInstallOptions() {
        verifyInstallOptions(InstallOptions.parse(
                        Options.parse(new String[]{"install"}).commandArgs, false),
                Set.of(), Map.of(), EnumSet.of(Scope.RUNTIME), "java-libs", null, false, true, false);

        verifyInstallOptions(InstallOptions.parse(
                        Options.parse(new String[]{"install", "-n"}).commandArgs, false),
                Set.of(), Map.of(), EnumSet.of(Scope.RUNTIME), "java-libs", null, false, false, false);

        verifyInstallOptions(InstallOptions.parse(
                        Options.parse(new String[]{"install", "-m"}).commandArgs, false),
                Set.of(), Map.of(), EnumSet.of(Scope.RUNTIME), null, null, false, true, true);

        verifyInstallOptions(InstallOptions.parse(
                        Options.parse(new String[]{"install", "-d", "foo", "--non-transitive"}).commandArgs, false),
                Set.of(), Map.of(), EnumSet.of(Scope.RUNTIME), "foo", null, false, false, false);

        verifyInstallOptions(InstallOptions.parse(
                        Options.parse(new String[]{"install", "-r", "the-repo"}).commandArgs, false),
                Set.of(), Map.of(), EnumSet.of(Scope.RUNTIME), null, "the-repo", false, true, false);

        verifyInstallOptions(InstallOptions.parse(
                        Options.parse(new String[]{"install", "-d", "foo", "--maven-local"}).commandArgs, false),
                Set.of(), Map.of(), EnumSet.of(Scope.RUNTIME), "foo", null, false, true, true);

        verifyInstallOptions(InstallOptions.parse(
                        Options.parse(new String[]{"install", "-O", "-s", "compile", "--scope", "test", "--repository", "repo", "-m"}).commandArgs, false),
                Set.of(), Map.of(), EnumSet.of(Scope.COMPILE, Scope.TEST), null, "repo", true, true, true);

        verifyInstallOptions(InstallOptions.parse(
                        Options.parse(new String[]{"install", "-x", "apache.*"}).commandArgs, false),
                Set.of(), Map.of("", Set.of("apache.*")), EnumSet.of(Scope.RUNTIME), "java-libs", null, false, true, false);

    }

    @Test
    void installOptionsOutDirAndRepoDirAreMutuallyExclusive() {
        assertThatThrownBy(() -> InstallOptions.parse(
                Options.parse(new String[]{"install", "--directory", "d", "--repository", "r"}).commandArgs, true))
                .isInstanceOf(JBuildException.class)
                .hasMessage("cannot specify both 'directory' and 'repository' options together." + LINE_END +
                        "Run jbuild --help for usage.");
    }

    @Test
    void installOptionsNonTransitiveAndRepoDirAreMutuallyExclusive() {
        assertThatThrownBy(() -> InstallOptions.parse(
                Options.parse(new String[]{"install", "-n", "--repository", "r"}).commandArgs, true))
                .isInstanceOf(JBuildException.class)
                .hasMessage("cannot specify both 'non-transitive' and 'repository' options together." + LINE_END +
                        "Run jbuild --help for usage.");

        assertThatThrownBy(() -> InstallOptions.parse(
                Options.parse(new String[]{"install", "-n", "-m"}).commandArgs, false))
                .isInstanceOf(JBuildException.class)
                .hasMessage("cannot specify both 'non-transitive' and 'maven-local' options together.");
    }

    @Test
    void moduleOptions() {
        verifyModuleOptions(ShowModulesOptions.parse(
                Options.parse(new String[]{}).commandArgs), List.of());
        verifyModuleOptions(ShowModulesOptions.parse(
                        Options.parse(new String[]{"module", "a/b/c", "b.jar"}).commandArgs),
                List.of("a/b/c", "b.jar"));
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
                                      String modulepath,
                                      Set<String> inputDirs,
                                      Set<String> resourcesDirs,
                                      Either<String, String> outputDirOrJar,
                                      String mainClass,
                                      Either<Boolean, String> manifest,
                                      String groovyJar) {
        assertEquals(classpath, options.classPath);
        assertEquals(modulepath, options.modulePath);
        assertEquals(inputDirs, options.inputDirectories);
        assertEquals(resourcesDirs, options.resourcesDirectories);
        assertEquals(outputDirOrJar, options.outputDirOrJar);
        assertEquals(mainClass, options.mainClass);
        assertEquals(manifest, options.manifest);
        assertEquals(groovyJar, options.groovyJar);
    }

    private void verifyInstallOptions(InstallOptions options,
                                      Set<String> artifacts,
                                      Map<String, Set<String>> exclusions,
                                      EnumSet<Scope> scopes,
                                      String outDir,
                                      String repoDir,
                                      boolean optional,
                                      boolean transitive,
                                      boolean mavenLocal
    ) {
        assertEquals(artifacts, options.artifacts, "artifacts");
        assertThatMap(exclusions).containsAllEntriesOf(mapValues(options.exclusions.getExclusions(), s -> s.stream()
                .map(Pattern::pattern)
                .collect(toSet())));
        assertEquals(mavenLocal, options.mavenLocal, "mavenLocal should be " + mavenLocal);
        assertEquals(optional, options.optional, "optional should be " + optional);
        assertEquals(outDir, options.outDir, "outDir");
        assertEquals(repoDir, options.repoDir, "repoDir");
        assertEquals(scopes, options.scopes, "scopes");
        assertEquals(transitive, options.transitive, "transitive should be " + transitive);
    }

    private void verifyModuleOptions(ShowModulesOptions options, List<String> modules) {
        assertThat(options.inputFiles).containsExactlyElementsOf(modules);
    }
}
