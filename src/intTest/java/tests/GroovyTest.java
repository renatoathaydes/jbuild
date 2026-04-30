package tests;

import jbuild.java.tools.Tools;
import org.junit.jupiter.api.Test;
import util.JBuildTestRunner;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static jbuild.java.tools.Tools.verifyToolSuccessful;
import static org.assertj.core.api.Assertions.assertThat;

public class GroovyTest extends JBuildTestRunner {

    @Test
    void canCompileGroovy4ToJarAndSourcesAndJavadocJar() throws Exception {
        var groovyJar = SystemProperties.integrationTestsRepo.toPath()
                .resolve(Paths.get("org", "apache", "groovy", "groovy",
                        Artifacts.GROOVY_VERSION, Artifacts.GROOVY_JAR_NAME));
        assert groovyJar.toFile().isFile();

        var groovydocToolClasspath = getGroovydocToolClasspath();
        assert !groovydocToolClasspath.isEmpty();

        var dir = Files.createTempDirectory(GroovyTest.class.getName());
        var rootDir = dir.resolve("mylib");
        var src = rootDir.resolve("src");
        var pkg = src.resolve("pkg");
        assert pkg.toFile().mkdirs();
        var myClassJava = pkg.resolve("MyClass.groovy");
        Files.write(myClassJava, List.of(
                "package pkg",
                "/**",
                " * This is Groovydoc.",
                " **/",
                "class MyClass {",
                "  /**",
                "   * The hello method.",
                "   **/",
                "  def hello() {}",
                "}"));
        var jar = rootDir.resolve(Paths.get("build", "mylib.jar"));
        var sourcesJar = rootDir.resolve(Paths.get("build", "mylib-sources.jar"));
        var javadocJar = rootDir.resolve(Paths.get("build", "mylib-javadoc.jar"));

        // compile jar, sources-jar and javadoc-jar
        var result = runWithIntTestRepo("--working-dir", rootDir.toString(), "compile",
                "--jar", jar.toAbsolutePath().toString(),
                "--groovy", groovyJar.toAbsolutePath().toString(),
                "--groovydoc-tool-class-path", groovydocToolClasspath,
                "--sources-jar",
                "--javadoc-jar");

        verifySuccessful("jbuild compile --groovy", result);

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
                        "pkg/MyClass.groovy");

        var javadocJarResult = Tools.Jar.create().listContents(javadocJar.toString());
        verifyToolSuccessful("jar tf", javadocJarResult);
        assertThat(javadocJarResult.getStdoutLines().collect(toList()))
                .contains(
                        "index.html",
                        "pkg/",
                        "pkg/MyClass.html");
    }
}
