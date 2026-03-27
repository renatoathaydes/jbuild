package jbuild.commands;

import jbuild.api.JBuildException;
import jbuild.java.ClassLoaderFactory;
import jbuild.util.Either;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;

import static jbuild.api.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.api.JBuildException.ErrorCause.IO_WRITE;
import static jbuild.java.ClasspathUtil.joinClasspath;

/**
 * Reflection-based invoker of the Groovy tool: {@code GroovyDocTool}.
 */
final class GroovyDocInvoker {

    private static final String GROOVYDOC_TOOL_ARGS_CLASS = "jbuild.groovy.GroovydocToolArguments";
    private static final String GROOVYDOC_TOOL_HELPER_CLASS = "jbuild.groovy.GroovydocToolHelper";

    static void run(List<String> sourceFiles,
                    String groovyJar,
                    String outputDir)
            throws Exception {
        var groovyClassLoader = createGroovyClassLoader(groovyJar);
        var helperClass = groovyClassLoader.loadClass(GROOVYDOC_TOOL_HELPER_CLASS);
        var argsClass = groovyClassLoader.loadClass(GROOVYDOC_TOOL_ARGS_CLASS);

        var sourceDirs = new String[]{Paths.get(".").toAbsolutePath().toString()};

        // String[] sourceDirs,
        // List<String> sourceFiles,
        // String outputDir
        var args = argsClass.getConstructor(String[].class, List.class, String.class)
                .newInstance(sourceDirs, sourceFiles, outputDir);

        var helper = (Callable<?>) helperClass.getConstructor(argsClass)
                .newInstance(args);

        helper.call();
    }

    private static ClassLoader createGroovyClassLoader(String groovyJar) {
        var jbuildGroovyJar = extractJBuildGroovyJar();

        // the groovy jar may not contain the GroovyDoc class, so we need to first check if there's an
        // environment variable that tells us what classpath to use to find it.
        var groovyDocClasspath = System.getenv("GROOVY_DOC_CLASSPATH");

        String fullClasspath = joinClasspath(jbuildGroovyJar,
                groovyDocClasspath == null ? groovyJar : groovyDocClasspath);

        return ClassLoaderFactory.createClassLoader(fullClasspath, ClassLoader.getSystemClassLoader());
    }

    private static String extractJBuildGroovyJar() {
        Path tempJar;
        try {
            tempJar = Files.createTempDirectory("jbuild-groovy-").resolve("jbuild-groovy.jar");
        } catch (IOException e) {
            throw new JBuildException("Cannot extract jbuild-groovy.jar: cannot create temp dir " + e, IO_WRITE);
        }

        Either<IOException, String> error = null;

        try (var jarStream = GroovyDocInvoker.class.getResourceAsStream("/jbuild-groovy.jar")) {
            if (jarStream == null) {
                error = Either.right("jbuild-groovy.jar is not embedded in the JBuild jar");
            } else {
                Files.copy(jarStream, tempJar);
            }
        } catch (IOException e) {
            error = Either.left(e);
        }

        if (error != null) {
            throw new JBuildException(
                    "Cannot extract jbuild-groovy.jar: " + error.map(Throwable::toString, s -> s),
                    error.map(ignore -> IO_WRITE, ignore -> ACTION_ERROR));
        }

        return tempJar.toString();
    }

}
