package jbuild.commands;

import jbuild.TestSystemProperties;
import jbuild.java.JavapOutputParser;
import jbuild.java.tools.Tools;
import jbuild.util.Either;
import jbuild.util.TestHelper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipFile;

import static java.util.stream.Collectors.toList;
import static jbuild.TestSystemProperties.groovyJar;
import static jbuild.java.JavapOutputParserTest.javap;
import static jbuild.java.tools.Tools.verifyToolSuccessful;
import static org.assertj.core.api.Assertions.assertThat;

public class CompileCommandExecutorTest {

    @Test
    void canComputeDefaultJarLocation() {
        var usrDir = System.getProperty("user.dir");
        var expectedUserDirJar = Paths.get("build", Paths.get(usrDir).getFileName() + ".jar").toString();

        var logEntry = TestHelper.createLog(false);
        var log = logEntry.getKey();

        var command = new CompileCommandExecutor(log);

        assertThat(command.jarOrDefault("", "")).isEqualTo(expectedUserDirJar);
        assertThat(command.jarOrDefault("", " ")).isEqualTo(expectedUserDirJar);
        assertThat(command.jarOrDefault("", "foo.jar")).isEqualTo("foo.jar");
        assertThat(command.jarOrDefault(".", "foo.jar")).isEqualTo(("foo.jar"));
        assertThat(command.jarOrDefault("..", "foo.jar")).isEqualTo(Paths.get("..", "foo.jar").toString());
        assertThat(command.jarOrDefault("wrk", "foo.jar")).isEqualTo(Paths.get("wrk", "foo.jar").toString());
    }

    @Test
    void canCompileClassFiles() throws Exception {
        var logEntry = TestHelper.createLog(false);
        var log = logEntry.getKey();
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var src1 = dir.resolve("src1");
        assert src1.toFile().mkdir();
        var fooJava = src1.resolve("Foo.java");
        Files.write(fooJava, List.of("public class Foo {}"));
        var src2 = dir.resolve("src2");
        assert src2.toFile().mkdir();
        var barJava = src2.resolve("Bar.java");
        Files.write(barJava, List.of("public class Bar {",
                "Bar() { new Foo(); }",
                "}"));
        var buildDir = dir.resolve("build");

        var result = command.compile(
                Set.of(src1.toString(), src2.toString()),
                Set.of("no-resources"),
                Either.left(buildDir.toString()),
                "",
                "",
                false,
                "",
                Either.left(true),
                List.of(),
                null);

        assertThat(result.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", result.getCompileResult().get());
        assertThat(result.getJarResult()).isNotPresent();
        assertThat(result.getSourcesJarResult()).isNotPresent();
        assertThat(result.getJavadocJarResult()).isNotPresent();

        var fooClass = buildDir.resolve("Foo.class");
        var barClass = buildDir.resolve("Bar.class");

        var buildFiles = buildDir.toFile().listFiles();

        assertThat(buildFiles).containsExactlyInAnyOrder(fooClass.toFile(), barClass.toFile());

        var types = new JavapOutputParser(log).processJavapOutput(
                javap(buildDir.toString(), "Foo", "Bar"));

        assertThat(types.keySet()).contains("LFoo;", "LBar;");
    }

    @Test
    void canCompileClassFilesOnWorkingDir() throws Exception {
        var logEntry = TestHelper.createLog(false);
        var log = logEntry.getKey();
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var src = dir.resolve("src");
        var pkg = src.resolve("pkg");
        assert pkg.toFile().mkdirs();
        var myClass = pkg.resolve("MyClass.java");
        Files.write(myClass, List.of("package pkg;\n" +
                "public class MyClass {}\n" +
                "final class OtherClass {}"));

        var buildDir = dir.resolve("build");

        // use workingDir argument, and all other paths relative to it
        var result = command.compile(
                dir.toString(),
                Set.of(),
                Set.of(),
                Either.left("build"),
                "",
                "",
                false,
                false,
                false,
                "",
                Either.left(true),
                List.of(),
                null);

        assertThat(result.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", result.getCompileResult().get());
        assertThat(result.getJarResult()).isNotPresent();
        assertThat(result.getSourcesJarResult()).isNotPresent();
        assertThat(result.getJavadocJarResult()).isNotPresent();

        var myClassFile = buildDir.resolve(Paths.get("pkg", "MyClass.class"));
        var otherClassFile = buildDir.resolve(Paths.get("pkg", "OtherClass.class"));

        var buildFiles = buildDir.resolve("pkg").toFile().listFiles();

        assertThat(buildFiles).containsExactlyInAnyOrder(myClassFile.toFile(), otherClassFile.toFile());
    }

    @Test
    void canCompileGroovyClassFilesOnWorkingDir() throws Exception {
        TestSystemProperties.validate("groovyJar", groovyJar);

        var logEntry = TestHelper.createLog(false);
        var log = logEntry.getKey();
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var src = dir.resolve("src");
        var pkg = src.resolve("pkg");
        assert pkg.toFile().mkdirs();
        var myClass = pkg.resolve("MyClass.groovy");
        Files.write(myClass, List.of("package pkg;\n" +
                "@groovy.transform.Immutable\n" +
                "class MyClass { String message }\n"));

        var buildDir = dir.resolve("build");

        // use workingDir argument, and all other paths relative to it
        var result = command.compile(
                dir.toString(),
                Set.of(),
                Set.of(),
                Either.left("build"),
                "",
                groovyJar.getAbsolutePath(),
                false,
                false,
                false,
                groovyJar.getAbsolutePath(),
                Either.left(true),
                List.of(),
                null);

        assertThat(result.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", result.getCompileResult().get());
        assertThat(result.getJarResult()).isNotPresent();
        assertThat(result.getSourcesJarResult()).isNotPresent();
        assertThat(result.getJavadocJarResult()).isNotPresent();

        var myClassFile = buildDir.resolve(Paths.get("pkg", "MyClass.class"));

        var buildFiles = buildDir.resolve("pkg").toFile().listFiles();

        assertThat(buildFiles).containsExactly(myClassFile.toFile());
    }

    @Test
    void canCompileToJarUsingClasspathOption() throws Exception {
        var logEntry = TestHelper.createLog(false);
        var log = logEntry.getKey();
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var src1 = dir.resolve("src1");
        assert src1.toFile().mkdir();
        var fooJava = src1.resolve("Foo.java");
        Files.write(fooJava, List.of("public class Foo {}"));
        var src2 = dir.resolve("src2");
        assert src2.toFile().mkdir();
        var barJava = src2.resolve("Bar.java");
        Files.write(barJava, List.of("public class Bar {",
                "Bar() { new Foo(); }",
                "}"));
        var buildDir = dir.resolve("build");
        var jar = dir.resolve("lib.jar");

        // first, compile Foo into a simple class file
        var result = command.compile(
                Set.of(src1.toString()),
                Set.of("no-resources"),
                Either.left(buildDir.toString()),
                "",
                "",
                false,
                "",
                Either.left(true),
                List.of(),
                null);

        assertThat(result.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", result.getCompileResult().get());
        assertThat(result.getJarResult()).isNotPresent();

        // then, compile Bar into a jar, using buildDir as its classpath
        result = command.compile(
                Set.of(src2.toString()),
                Set.of("no-resources"),
                Either.right(jar.toString()),
                "",
                "",
                false,
                buildDir.toString(),
                Either.left(true),
                List.of(),
                null);

        assertThat(result.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", result.getCompileResult().get());
        assertThat(result.getJarResult()).isPresent();
        verifyToolSuccessful("jar", result.getJarResult().get());

        var fooClass = buildDir.resolve("Foo.class");
        var buildFiles = buildDir.toFile().listFiles();

        assertThat(buildFiles).containsExactlyInAnyOrder(fooClass.toFile());

        var types = new JavapOutputParser(log).processJavapOutput(
                javap(jar.toString(), "Bar"));

        assertThat(types.keySet()).contains("LBar;");
    }

    @Test
    void canCompileToJarUsingCustomManifest() throws Exception {
        var logEntry = TestHelper.createLog(false);
        var log = logEntry.getKey();
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var src = dir.resolve("src");
        assert src.toFile().mkdir();
        var fooJava = src.resolve("Foo.java");
        Files.write(fooJava, List.of("public class Foo {}"));
        var manifestFile = dir.resolve("MANIFEST.txt");
        Files.write(manifestFile, List.of(
                "Implementation-Title: JBuild Tests",
                "Implementation-Version: 0.1.0"
        ));
        var buildDir = dir.resolve("build");
        var jar = dir.resolve("lib.jar");

        var result = command.compile(
                Set.of(src.toString()),
                Set.of("no-resources"),
                Either.right(jar.toString()),
                "",
                "",
                false,
                "",
                // Manifest location
                Either.right(manifestFile.toString()),
                List.of(),
                null);

        assertThat(result.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", result.getCompileResult().get());
        assertThat(result.getJarResult()).isPresent();
        verifyToolSuccessful("jar", result.getJarResult().get());

        var jarCode = new ProcessBuilder("jar", "xf", jar.toString())
                .directory(dir.toFile())
                .start()
                .waitFor();

        assertThat(jarCode).isZero();

        var generatedManifestLines = Files.readAllLines(dir.resolve(Paths.get("META-INF", "MANIFEST.MF")));

        assertThat(generatedManifestLines).hasSize(5);
        // two lines are generated by the compiler,
        // example:
        //   Manifest-Version: 1.0
        //   Created-By: 1.7.0_06 (Oracle Corporation)
        assertThat(generatedManifestLines.get(0)).startsWith("Manifest-Version: ");
        assertThat(generatedManifestLines.get(1)).isEqualTo("Implementation-Title: JBuild Tests");
        assertThat(generatedManifestLines.get(2)).isEqualTo("Implementation-Version: 0.1.0");
        assertThat(generatedManifestLines.get(3)).startsWith("Created-By: ");
        assertThat(generatedManifestLines.get(4)).isEqualTo("");

    }

    @Test
    void canCompileToJarOnWorkingDirUsingJbExtensionOption() throws Exception {
        TestSystemProperties.validate("jbApiJar", TestSystemProperties.jbApiJar);

        var logEntry = TestHelper.createLog(false);
        var log = logEntry.getKey();
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var rootDir = dir.resolve("mylib");
        var src = rootDir.resolve("src");
        var pkg = src.resolve("pkg");
        assert pkg.toFile().mkdirs();
        var barJava = pkg.resolve("MyExtension.java");
        Files.write(barJava, List.of(
                "package pkg;",
                "import jbuild.api.*;",
                "@JbTaskInfo(name = \"my-extension\")",
                "public class MyExtension implements JbTask {",
                "  @Override",
                "  public void run(String... args) {}",
                "}"));
        var jar = rootDir.resolve(Paths.get("build", "mylib.jar"));

        // compile jar as a jb extension
        var result = command.compile(
                rootDir.toString(),
                Set.of(),
                Set.of(),
                Either.right(""), // let the default jar name be used
                "",
                "",
                true,
                false,
                false,
                TestSystemProperties.jbApiJar.getAbsolutePath(),
                Either.left(true),
                List.of(),
                null);

        assertThat(result.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", result.getCompileResult().get());
        assertThat(result.getJarResult()).isPresent();
        verifyToolSuccessful("jar", result.getJarResult().get());

        var jarResult = Tools.Jar.create().listContents(jar.toString());
        verifyToolSuccessful("jar tf", jarResult);
        assertThat(jarResult.getStdoutLines().collect(toList()))
                .containsExactlyInAnyOrder(
                        "META-INF/",
                        "META-INF/MANIFEST.MF",
                        "META-INF/jb/",
                        "META-INF/jb/jb-extension.yaml",
                        "pkg/",
                        "pkg/MyExtension.class");
    }

    @Test
    void canCompileToJarAndSourcesAndJavadocJar() throws Exception {
        var logEntry = TestHelper.createLog(false);
        var log = logEntry.getKey();
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var rootDir = dir.resolve("mylib");
        var src = rootDir.resolve("src");
        var pkg = src.resolve("pkg");
        assert pkg.toFile().mkdirs();
        var myClassJava = pkg.resolve("MyClass.java");
        Files.write(myClassJava, List.of(
                "package pkg;",
                "/**",
                " * This is Javadoc.",
                " **/",
                "public class MyClass {",
                "  /**",
                "   * The hello method.",
                "   **/",
                "  public void hello() {}",
                "}"));
        var jar = rootDir.resolve(Paths.get("build", "mylib.jar"));
        var sourcesJar = rootDir.resolve(Paths.get("build", "mylib-sources.jar"));
        var javadocJar = rootDir.resolve(Paths.get("build", "mylib-javadoc.jar"));

        // compile jar, sources-jar and javadoc-jar
        var result = command.compile(
                rootDir.toString(),
                Set.of(),
                Set.of(),
                Either.right(""), // let the default jar name be used
                "",
                "",
                false,
                true,
                true,
                "",
                Either.left(true),
                List.of(),
                null);

        assertThat(result.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", result.getCompileResult().get());
        assertThat(result.getJarResult()).isPresent();
        verifyToolSuccessful("jar", result.getJarResult().get());
        assertThat(result.getSourcesJarResult()).isPresent();
        verifyToolSuccessful("sourcesJar", result.getSourcesJarResult().get());
        assertThat(result.getJavadocJarResult()).isPresent();
        verifyToolSuccessful("javadocJar", result.getJavadocJarResult().get());

        var jarResult = Tools.Jar.create().listContents(jar.toString());
        verifyToolSuccessful("jar tf", jarResult);
        assertThat(jarResult.getStdoutLines().collect(toList()))
                .containsExactlyInAnyOrder(
                        "META-INF/",
                        "META-INF/MANIFEST.MF",
                        "pkg/",
                        "pkg/MyClass.class");

        var sourceJarResult = Tools.Jar.create().listContents(sourcesJar.toString());
        verifyToolSuccessful("jar tf", sourceJarResult);
        assertThat(sourceJarResult.getStdoutLines().collect(toList()))
                .containsExactlyInAnyOrder(
                        "META-INF/",
                        "META-INF/MANIFEST.MF",
                        "pkg/",
                        "pkg/MyClass.java");

        var javadocJarResult = Tools.Jar.create().listContents(javadocJar.toString());
        verifyToolSuccessful("jar tf", javadocJarResult);
        assertThat(javadocJarResult.getStdoutLines().collect(toList()))
                .contains(
                        "index.html",
                        "pkg/",
                        "pkg/MyClass.html");
    }

    @Test
    void canCompileJavaModuleToJarAndSourcesAndJavadocJar() throws Exception {
        var logEntry = TestHelper.createLog(false);
        var log = logEntry.getKey();
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var rootDir = dir.resolve("mylib");
        var src = rootDir.resolve("src");
        var pkg = src.resolve("pkg");
        assert pkg.toFile().mkdirs();
        var myClassJava = pkg.resolve("MyClass.java");
        Files.write(myClassJava, List.of(
                "package pkg;",
                "/**",
                " * This is Javadoc.",
                " **/",
                "public class MyClass {",
                "  /**",
                "   * The hello method.",
                "   **/",
                "  public String hello() { return \"Hi\"; }",
                "}"));
        var moduleFile = src.resolve("module-info.java");
        Files.write(moduleFile, List.of(
                "/**",
                " * My library module.",
                " **/",
                "module mylib {",
                "  exports pkg;",
                "}"));
        var jar = rootDir.resolve(Paths.get("build", "mylib.jar"));
        var sourcesJar = rootDir.resolve(Paths.get("build", "mylib-sources.jar"));
        var javadocJar = rootDir.resolve(Paths.get("build", "mylib-javadoc.jar"));

        // compile jar, sources-jar and javadoc-jar
        var result = command.compile(
                rootDir.toString(),
                Set.of(),
                Set.of(),
                Either.right(""), // let the default jar name be used
                "",
                "",
                false,
                true,
                true,
                "",
                Either.left(true),
                List.of(),
                null);

        assertThat(result.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", result.getCompileResult().get());
        assertThat(result.getJarResult()).isPresent();
        verifyToolSuccessful("jar", result.getJarResult().get());
        assertThat(result.getSourcesJarResult()).isPresent();
        verifyToolSuccessful("sourcesJar", result.getSourcesJarResult().get());
        assertThat(result.getJavadocJarResult()).isPresent();
        verifyToolSuccessful("javadocJar", result.getJavadocJarResult().get());

        var jarResult = Tools.Jar.create().listContents(jar.toString());
        verifyToolSuccessful("jar tf", jarResult);
        assertThat(jarResult.getStdoutLines().collect(toList()))
                .containsExactlyInAnyOrder(
                        "META-INF/",
                        "META-INF/MANIFEST.MF",
                        "module-info.class",
                        "pkg/",
                        "pkg/MyClass.class");

        var sourceJarResult = Tools.Jar.create().listContents(sourcesJar.toString());
        verifyToolSuccessful("jar tf", sourceJarResult);
        assertThat(sourceJarResult.getStdoutLines().collect(toList()))
                .containsExactlyInAnyOrder(
                        "META-INF/",
                        "META-INF/MANIFEST.MF",
                        "module-info.java",
                        "pkg/",
                        "pkg/MyClass.java");

        var javadocJarResult = Tools.Jar.create().listContents(javadocJar.toString());
        verifyToolSuccessful("jar tf", javadocJarResult);
        assertThat(javadocJarResult.getStdoutLines().collect(toList()))
                .contains(
                        "index.html",
                        "mylib/",
                        "mylib/module-summary.html",
                        "mylib/pkg/",
                        "mylib/pkg/MyClass.html");

        // now check that a Java Module can rely on another Module in the module-path
        var rootDir2 = dir.resolve("other");
        var src2 = rootDir2.resolve("src");
        var pkg2 = src2.resolve("other_pkg");
        assert pkg2.toFile().mkdirs();
        var otherClassJava = pkg2.resolve("Other.java");
        Files.write(otherClassJava, List.of(
                "package other_pkg;",
                "public class Other {",
                "  public static void main(String[] args) {",
                "    System.out.println(new pkg.MyClass().hello());",
                "  }",
                "}"));
        var moduleFile2 = src2.resolve("module-info.java");
        Files.write(moduleFile2, List.of(
                "module another {",
                "  requires mylib;",
                "}"));
        var jar2 = rootDir2.resolve(Paths.get("build", "other.jar"));

        // compile jar, sources-jar and javadoc-jar
        var result2 = command.compile(
                rootDir2.toString(),
                Set.of("src"),
                Set.of(),
                Either.right(rootDir2.relativize(jar2).toString()),
                "other_pkg.Other",
                "",
                false,
                false,
                false,
                "",
                "../mylib/build/mylib.jar",
                Either.left(true),
                List.of(),
                null);

        assertThat(result2.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", result2.getCompileResult().get());

        assertThat(jar2).isRegularFile();

        // to verify that the module was compile properly, try to run it as a module
        var javaProcBuilder = new ProcessBuilder("java",
                "--module-path", String.join(File.pathSeparator, jar.toString(), jar2.toString()),
                // run the module by name, the main-class should be in the MANIFEST
                "-m", "another");
        javaProcBuilder.directory(dir.toFile());
        var javaProc = javaProcBuilder.start();
        var javaExitCode = javaProc.waitFor();

        assertThat(javaExitCode).isZero();
        assertThat(javaProc.getInputStream()).hasContent("Hi");
    }

    @Test
    void canCopyResources() throws Exception {
        var logEntry = TestHelper.createLog(false);
        var log = logEntry.getKey();
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());

        var src = dir.resolve("src");
        assert src.toFile().mkdir();
        var srcFooJava = src.resolve("Foo.java");
        Files.write(srcFooJava, List.of("public class Foo {}"));

        var resDir = dir.resolve("resources");
        assert resDir.toFile().mkdir();
        var fooJava = resDir.resolve("Foo.java");
        Files.write(fooJava, List.of("public class Foo {}"));
        var myRes = resDir.resolve("my-resource.txt");
        Files.write(myRes, List.of("hello"));

        var resFooDir = resDir.resolve("foo");
        assert resFooDir.toFile().mkdir();
        var myFooRes = resFooDir.resolve("my-foo.txt");
        Files.write(myFooRes, List.of("foo bar"));

        var outDir = dir.resolve("output");

        command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of(resDir.toFile().getAbsolutePath()),
                Either.left(outDir.toFile().getAbsolutePath()),
                "",
                "",
                false,
                "",
                Either.left(true),
                List.of(),
                null);

        var outMyRes = outDir.resolve(myRes.getFileName().toString());
        var outMyFooRes = outDir.resolve(Paths.get("foo", myFooRes.getFileName().toString()));

        assertThat(outDir).isDirectory();
        assertThat(outMyRes).isRegularFile().hasContent("hello");
        assertThat(outMyFooRes).isRegularFile().hasContent("foo bar");
    }

    @Test
    void incrementalCompilationWithOnlyModifiedFile() throws Exception {
        var logEntry = TestHelper.createLog(false);
        var log = logEntry.getKey();
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var jar = dir.resolve("lib.jar");
        var src = dir.resolve("src");
        assert src.toFile().mkdir();

        var utilJava = src.resolve("Util.java");
        Files.write(utilJava, List.of("class Util {",
                "  static String message() { return \"hello world\"; }",
                "}"));

        var mainJava = src.resolve("Main.java");
        Files.write(mainJava, List.of("class Main {",
                "  public static void main(String[] args) {",
                "    System.out.println(Util.message());",
                "  }",
                "}"));

        // initial compilation
        final var firstResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of(),
                Either.right(jar.toFile().getAbsolutePath()),
                "Main",
                "",
                false,
                "",
                Either.left(true),
                List.of(),
                null);

        assertThat(firstResult.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", firstResult.getCompileResult().get());
        assertThat(firstResult.getJarResult()).isPresent();
        verifyToolSuccessful("jar", firstResult.getJarResult().get());
        assertThat(jar).isRegularFile();

        // change the Util class
        Files.write(utilJava, List.of("class Util {",
                "  static String message() { return \"bye world\"; }",
                "}"));

        // incremental compilation
        final var secondResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of(),
                Either.right(jar.toFile().getAbsolutePath()),
                "Main",
                "",
                false,
                "",
                Either.left(true),
                List.of(),
                new IncrementalChanges(Set.of(), Set.of(utilJava.toString())));

        assertThat(secondResult.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", secondResult.getCompileResult().get());
        assertThat(secondResult.getJarResult()).isPresent();
        verifyToolSuccessful("jar", secondResult.getJarResult().get());

        // Run the jar to make sure the change was handled correctly
        var javaProc = new ProcessBuilder("java", "-jar", jar.toString()).start();
        var javaExitCode = javaProc.waitFor();

        assertThat(javaExitCode).isZero();
        assertThat(javaProc.getInputStream()).hasContent("bye world");
    }

    @Test
    void incrementalCompilationWithOnlyModifiedResourceFile() throws Exception {
        var logEntry = TestHelper.createLog(false);
        var log = logEntry.getKey();
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var jar = dir.resolve("lib.jar");
        var src = dir.resolve("src");
        assert src.toFile().mkdir();
        var rsrc = dir.resolve("resources");
        assert rsrc.toFile().mkdir();

        var resource = rsrc.resolve("res.txt");
        Files.write(resource, List.of("my resource"));

        var mainJava = src.resolve("Main.java");
        Files.write(mainJava, List.of("class Main {",
                "  public static void main(String[] args) {",
                "    System.out.println(Main.class.getResource(\"res.txt\"));",
                "  }",
                "}"));

        // initial compilation
        final var firstResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of(rsrc.toFile().getAbsolutePath()),
                Either.right(jar.toFile().getAbsolutePath()),
                "Main",
                "",
                false,
                "",
                Either.left(true),
                List.of(),
                null);

        assertThat(firstResult.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", firstResult.getCompileResult().get());
        assertThat(firstResult.getJarResult()).isPresent();
        verifyToolSuccessful("jar", firstResult.getJarResult().get());
        assertThat(jar).isRegularFile();

        // change the resource only
        Files.write(resource, List.of("new resource contents"));

        // incremental compilation
        final var secondResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of(rsrc.toFile().getAbsolutePath()),
                Either.right(jar.toFile().getAbsolutePath()),
                "Main",
                "",
                false,
                "",
                Either.left(true),
                List.of(),
                new IncrementalChanges(Set.of(), Set.of(resource.toString())));

        assertThat(secondResult.getCompileResult()).isEmpty();
        assertThat(secondResult.getJarResult()).isPresent();
        verifyToolSuccessful("jar", secondResult.getJarResult().get());

        var patchedJarContents = Tools.Jar.create().listContents(jar.toString()).getStdoutLines()
                .collect(toList());

        assertThat(patchedJarContents).containsExactlyInAnyOrder("META-INF/", "META-INF/MANIFEST.MF",
                "Main.class", resource.getFileName().toString());

        assertThat(unzipEntry(jar, resource.getFileName().toString()))
                .isEqualToIgnoringNewLines("new resource contents");
    }

    @Test
    void incrementalCompilationWithOnlyDeletedResourceFileJar() throws Exception {
        var logEntry = TestHelper.createLog(false);
        var log = logEntry.getKey();
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var jar = dir.resolve("lib.jar");
        var src = dir.resolve("src");
        assert src.toFile().mkdirs();
        var rsrc = dir.resolve("resources");
        assert rsrc.resolve("assets").toFile().mkdirs();

        var resource = rsrc.resolve(Paths.get("assets", "style.css"));
        Files.write(resource, List.of("body { color: red; }"));

        var mainJava = src.resolve("Main.java");
        Files.write(mainJava, List.of("class Main {",
                "  public static void main(String[] args) {",
                "    System.out.println(Main.class.getResource(\"assets/style.css\"));",
                "  }",
                "}"));

        // initial compilation
        final var firstResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of(rsrc.toFile().getAbsolutePath()),
                Either.right(jar.toFile().getAbsolutePath()),
                "Main",
                "",
                false,
                "",
                Either.left(true),
                List.of(),
                null);

        assertThat(firstResult.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", firstResult.getCompileResult().get());
        assertThat(firstResult.getJarResult()).isPresent();
        verifyToolSuccessful("jar", firstResult.getJarResult().get());
        assertThat(jar).isRegularFile();

        // delete the resource dir
        assert resource.toFile().delete();
        assert resource.toFile().getParentFile().delete();

        // incremental compilation
        final var secondResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of(rsrc.toFile().getAbsolutePath()),
                Either.right(jar.toFile().getAbsolutePath()),
                "Main",
                "",
                false,
                "",
                Either.left(true),
                List.of(),
                new IncrementalChanges(Set.of(resource.toString()), Set.of()));

        assertThat(secondResult.getCompileResult()).isEmpty();
        assertThat(secondResult.getJarResult()).isEmpty();

        var patchedJarContents = Tools.Jar.create().listContents(jar.toString()).getStdoutLines()
                .collect(toList());

        assertThat(patchedJarContents).containsExactlyInAnyOrder("META-INF/", "META-INF/MANIFEST.MF", "Main.class");
    }

    @Test
    void incrementalCompilationWithOnlyDeletedResourceFileDir() throws Exception {
        var logEntry = TestHelper.createLog(false);
        var log = logEntry.getKey();
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var outputDir = dir.resolve("build");
        var src = dir.resolve("src");
        assert src.toFile().mkdirs();
        var rsrc = dir.resolve("resources");
        assert rsrc.resolve("assets").toFile().mkdirs();

        var resource = rsrc.resolve(Paths.get("assets", "style.css"));
        Files.write(resource, List.of("body { color: red; }"));

        var mainJava = src.resolve("Main.java");
        Files.write(mainJava, List.of("class Main {",
                "  public static void main(String[] args) {",
                "    System.out.println(Main.class.getResource(\"assets/style.css\"));",
                "  }",
                "}"));

        // initial compilation
        final var firstResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of(rsrc.toFile().getAbsolutePath()),
                Either.left(outputDir.toFile().getAbsolutePath()),
                "Main",
                "",
                false,
                "",
                Either.left(true),
                List.of(),
                null);

        assertThat(firstResult.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", firstResult.getCompileResult().get());
        assertThat(firstResult.getJarResult()).isEmpty();
        assertThat(outputDir).isDirectory();

        // delete the resource dir
        assert resource.toFile().delete();
        assert resource.toFile().getParentFile().delete();

        // incremental compilation
        final var secondResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of(rsrc.toFile().getAbsolutePath()),
                Either.left(outputDir.toFile().getAbsolutePath()),
                "Main",
                "",
                false,
                "",
                Either.left(true),
                List.of(),
                new IncrementalChanges(Set.of(resource.toString()), Set.of()));

        assertThat(secondResult.getCompileResult()).isEmpty();
        assertThat(secondResult.getJarResult()).isEmpty();

        var expectedOutput = Set.of(Paths.get("META-INF"),
                Paths.get("META-INF", "MANIFEST.MF"),
                Paths.get("Main.class"));

        assertThat(outputDir).isDirectoryRecursivelyContaining((file) ->
                expectedOutput.contains(outputDir.relativize(file)));
    }

    @Test
    void incrementalCompilationWithModifiedAndAddedFiles() throws Exception {
        var logEntry = TestHelper.createLog(false);
        var log = logEntry.getKey();
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var jar = dir.resolve("lib.jar");
        var src = dir.resolve("src");
        assert src.toFile().mkdir();

        var utilJava = src.resolve("Util.java");
        Files.write(utilJava, List.of("class Util {",
                "  static String message() { return \"hello world\"; }",
                "}"));

        var mainJava = src.resolve("Main.java");
        Files.write(mainJava, List.of("class Main {",
                "  public static void main(String[] args) {",
                "    System.out.println(Util.message());",
                "  }",
                "}"));

        // initial compilation
        final var firstResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of(),
                Either.right(jar.toFile().getAbsolutePath()),
                "Main",
                "",
                false,
                "",
                Either.left(true),
                List.of(),
                null);

        assertThat(firstResult.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", firstResult.getCompileResult().get());
        assertThat(firstResult.getJarResult()).isPresent();
        verifyToolSuccessful("jar", firstResult.getJarResult().get());
        assertThat(jar).isRegularFile();

        // change the Util class to delegate to another, new class
        Files.write(utilJava, List.of("class Util {",
                "  static String message() { return SecondaryUtil.msg(); }",
                "}"));

        // create the new class
        var util2Java = src.resolve("SecondaryUtil.java");
        Files.write(util2Java, List.of("class SecondaryUtil {",
                "  static String msg() { return \"secondary util message\"; }",
                "}"));

        // incremental compilation
        final var secondResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of(),
                Either.right(jar.toFile().getAbsolutePath()),
                "Main",
                "",
                false,
                "",
                Either.left(true),
                List.of(),
                new IncrementalChanges(Set.of(),
                        Set.of(utilJava.toString(), util2Java.toString())));

        assertThat(secondResult.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", secondResult.getCompileResult().get());
        assertThat(secondResult.getJarResult()).isPresent();
        verifyToolSuccessful("jar", secondResult.getJarResult().get());

        // Run the jar to make sure the change was handled correctly
        var javaProc = new ProcessBuilder("java", "-jar", jar.toString()).start();
        var javaExitCode = javaProc.waitFor();

        assertThat(javaExitCode).isZero();
        assertThat(javaProc.getInputStream()).hasContent("secondary util message");
    }

    @Test
    void incrementalCompilationWithModifiedAndDeletedFile() throws Exception {
        var logEntry = TestHelper.createLog(false);
        var log = logEntry.getKey();
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var jar = dir.resolve("lib.jar");
        var src = dir.resolve("src");
        assert src.toFile().mkdir();

        var utilJava = src.resolve("Util.java");
        Files.write(utilJava, List.of("class Util {",
                "  static String message() { return \"hello world\"; }",
                "}"));

        var mainJava = src.resolve("Main.java");
        Files.write(mainJava, List.of("class Main {",
                "  public static void main(String[] args) {",
                "    System.out.println(Util.message());",
                "  }",
                "}"));

        // initial compilation
        final var firstResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of("no-resources"),
                Either.right(jar.toFile().getAbsolutePath()),
                "Main",
                "",
                false,
                "",
                Either.left(true),
                List.of(),
                null);

        assertThat(firstResult.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", firstResult.getCompileResult().get());
        assertThat(firstResult.getJarResult()).isPresent();
        verifyToolSuccessful("jar", firstResult.getJarResult().get());
        assertThat(jar).isRegularFile();

        // delete the Util class
        assert utilJava.toFile().delete();

        // make Main independent of Util
        Files.write(mainJava, List.of("class Main {",
                "  public static void main(String[] args) {",
                "    System.out.println(\"Main hello world\");",
                "  }",
                "}"));

        // incremental compilation
        final var secondResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of("no-resources"),
                Either.right(jar.toFile().getAbsolutePath()),
                "Main",
                "",
                false,
                "",
                Either.left(true),
                List.of(),
                new IncrementalChanges(Set.of("Util.class"), Set.of(mainJava.toString())));

        assertThat(secondResult.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", secondResult.getCompileResult().get());
        assertThat(secondResult.getJarResult()).isPresent();
        verifyToolSuccessful("jar", secondResult.getJarResult().get());

        // Run the jar to make sure the change was handled correctly
        var javaProc = new ProcessBuilder("java", "-jar", jar.toString()).start();
        var javaExitCode = javaProc.waitFor();

        assertThat(javaExitCode).isZero();
        assertThat(javaProc.getInputStream()).hasContent("Main hello world");

        // make sure the jar was patched correctly
        var patchedJarContents = Tools.Jar.create().listContents(jar.toString()).getStdoutLines()
                .collect(toList());

        assertThat(patchedJarContents).containsExactly("META-INF/", "META-INF/MANIFEST.MF", "Main.class");
    }

    @Test
    void incrementalCompilationWithDeletedSourceFile() throws Exception {
        var logEntry = TestHelper.createLog(true);
        var log = logEntry.getKey();
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var outDir = dir.resolve("dist");
        var src = dir.resolve("src");
        assert src.toFile().mkdir();
        assert src.resolve("utils").toFile().mkdir();
        assert src.resolve("app").toFile().mkdir();

        var utilJava = src.resolve("utils").resolve("Util.java");
        Files.write(utilJava, List.of("package utils;", "class Util { }"));

        var mainJava = src.resolve("app").resolve("Main.java");
        Files.write(mainJava, List.of("package app;", "class Main { }"));

        // initial compilation
        final var firstResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of("no-resources"),
                Either.left(outDir.toFile().getAbsolutePath()),
                "app.Main",
                "",
                false,
                "",
                Either.left(true),
                List.of(),
                null);

        assertThat(firstResult.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", firstResult.getCompileResult().get());
        assertThat(firstResult.getJarResult()).isNotPresent();
        assertThat(outDir).isDirectory();
        assertThat(outDir.resolve(Paths.get("utils", "Util.class"))).isRegularFile();
        assertThat(outDir.resolve(Paths.get("app", "Main.class"))).isRegularFile();

        // delete the Util class
        assert utilJava.toFile().delete();

        // incremental compilation
        final var secondResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of("no-resources"),
                Either.left(outDir.toFile().getAbsolutePath()),
                "app.Main",
                "",
                false,
                "",
                Either.left(true),
                List.of(),
                new IncrementalChanges(
                        // jb knows the path to the deleted class files (one Java file may cause many class files to be deleted)
                        Set.of(Paths.get("utils", "Util.class").toString()),
                        Set.of()));

        System.out.println(logEntry.getValue());

        // no result is present as there was nothing to compile
        assertThat(secondResult.getCompileResult()).isNotPresent();
        assertThat(secondResult.getJarResult()).isNotPresent();
        assertThat(outDir).isDirectory();
        assertThat(outDir.resolve(Paths.get("utils", "Util.class"))).doesNotExist();
        assertThat(outDir.resolve(Paths.get("app", "Main.class"))).isRegularFile();
    }

    @Test
    void incrementalCompilationWithModifiedAndDeletedFileInPackageAndOutputDir() throws Exception {
        var logEntry = TestHelper.createLog(false);
        var log = logEntry.getKey();
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var outputDir = dir.resolve("build");
        var src = dir.resolve("src");
        assert src.toFile().mkdir();
        var util = src.resolve("util");
        assert util.toFile().mkdir();
        var main = src.resolve("main");
        assert main.toFile().mkdir();

        var utilJava = util.resolve("Util.java");
        Files.write(utilJava, List.of("package util; public class Util {",
                "  public static String message() { return \"hello world\"; }",
                "}"));

        var mainJava = main.resolve("Main.java");
        Files.write(mainJava, List.of("package main; import util.Util; class Main {",
                "  public static void main(String[] args) {",
                "    System.out.println(Util.message());",
                "  }",
                "}"));

        // initial compilation
        final var firstResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of("no-resources"),
                Either.left(outputDir.toFile().getAbsolutePath()),
                "",
                "",
                false,
                "",
                Either.left(true),
                List.of(),
                null);

        assertThat(firstResult.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", firstResult.getCompileResult().get());
        assertThat(firstResult.getJarResult()).isNotPresent();
        assertThat(outputDir).isDirectory();

        // delete the Util class
        assert utilJava.toFile().delete();
        assert util.toFile().delete();

        // make Main independent of Util
        Files.write(mainJava, List.of("package main; class Main {",
                "  public static void main(String[] args) {",
                "    System.out.println(\"Main hello world\");",
                "  }",
                "}"));

        // incremental compilation
        final var secondResult = command.compile(Set.of(src.toFile().getAbsolutePath()),
                Set.of("no-resources"),
                Either.left(outputDir.toFile().getAbsolutePath()),
                "",
                "",
                false,
                "",
                Either.left(true),
                List.of(),
                new IncrementalChanges(Set.of(Paths.get("util", "Util.class").toString()), Set.of(mainJava.toString())));

        assertThat(secondResult.getCompileResult()).isPresent();
        verifyToolSuccessful("compile", secondResult.getCompileResult().get());
        assertThat(secondResult.getJarResult()).isNotPresent();

        // Run the jar to make sure the change was handled correctly
        var javaProc = new ProcessBuilder("java", "-cp", outputDir.toString(), "main.Main").start();
        var javaExitCode = javaProc.waitFor();

        assertThat(javaExitCode).isZero();
        assertThat(javaProc.getInputStream()).hasContent("Main hello world");

        // make sure the class files were patched correctly
        assertThat(outputDir).isDirectoryContaining(path ->
                Objects.equals("main", path.getFileName().toString()));

        var outMain = outputDir.resolve("main");
        assertThat(outMain).isDirectoryContaining(path ->
                Objects.equals("Main.class", path.getFileName().toString()));
        assertThat(outMain.resolve("Main.class")).isRegularFile();
    }

    @Test
    void reportsJavaCompilationError() throws Exception {
        var logEntry = TestHelper.createLog(false);
        var log = logEntry.getKey();
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var src = dir.resolve("src");
        var pkg = src.resolve("pkg");
        assert pkg.toFile().mkdirs();
        var myClass = pkg.resolve("MyClass.java");
        Files.write(myClass, List.of("package pkg;\n" +
                "public class MyClass {\n" +
                "  public MyOtherType doesNotExist;" +
                "}"));

        // use workingDir argument, and all other paths relative to it
        var result = command.compile(
                dir.toString(),
                Set.of(),
                Set.of(),
                Either.left("build"),
                "",
                "",
                false,
                false,
                false,
                "",
                Either.left(true),
                List.of(),
                null);

        assertThat(result.getCompileResult()).isPresent();
        assertThat(result.getCompileResult().get().exitCode()).isEqualTo(1);
        assertThat(result.getCompileResult().get().getStderr()).contains("MyClass.java:3: error: cannot find symbol");
    }

    @Test
    void reportsGroovyCompilationError() throws Exception {
        var logEntry = TestHelper.createLog(false);
        var log = logEntry.getKey();
        var command = new CompileCommandExecutor(log);

        var dir = Files.createTempDirectory(CompileCommandExecutorTest.class.getName());
        var src = dir.resolve("src");
        var pkg = src.resolve("pkg");
        assert pkg.toFile().mkdirs();
        var myClass = pkg.resolve("MyClass.groovy");
        Files.write(myClass, List.of("package pkg;\n" +
                "class MyClass {\n" +
                "  MyOtherType doesNotExist;\n" +
                "}"));

        // use workingDir argument, and all other paths relative to it
        var result = command.compile(
                dir.toString(),
                Set.of(),
                Set.of(),
                Either.left("build"),
                "",
                groovyJar.getAbsolutePath(),
                false,
                false,
                false,
                "",
                Either.left(true),
                List.of(),
                null);

        assertThat(result.getCompileResult()).isPresent();
        assertThat(result.getCompileResult().get().exitCode()).isEqualTo(1);
        assertThat(result.getCompileResult().get().getStderr())
                .contains("MyClass.groovy: 3: unable to resolve class MyOtherType");
    }

    private static String unzipEntry(Path jar, String entry) throws Exception {
        try (var zip = new ZipFile(jar.toFile())) {
            var rsrcEntry = zip.getEntry(entry);
            return new String(zip.getInputStream(rsrcEntry).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

}
