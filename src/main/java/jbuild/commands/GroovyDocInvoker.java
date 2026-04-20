package jbuild.commands;

import jbuild.api.JBuildException;
import jbuild.java.ClassLoaderFactory;
import jbuild.util.Either;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
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

    static void run(Collection<String> sourceDirs,
                    Collection<String> sourceFiles,
                    String groovyJar,
                    String groovydocToolClasspath,
                    String outputDir)
            throws Exception {
        var groovyClassLoader = createGroovyClassLoader(groovyJar, groovydocToolClasspath);
        var helperClass = groovyClassLoader.loadClass(GROOVYDOC_TOOL_HELPER_CLASS);
        var argsClass = groovyClassLoader.loadClass(GROOVYDOC_TOOL_ARGS_CLASS);

        // String[] sourceDirs,
        // Collection<String> sourceFiles,
        // String outputDir
        var args = argsClass.getConstructor(String[].class, Collection.class, String.class)
                .newInstance(sourceDirs.toArray(String[]::new), sourceFiles, outputDir);

        var helper = (Callable<?>) helperClass.getConstructor(argsClass)
                .newInstance(args);

        helper.call();
    }

    private static ClassLoader createGroovyClassLoader(String groovyJar, String groovydocToolClasspath) {
        var jbuildGroovyJar = extractJBuildGroovyJar();

        String fullClasspath = joinClasspath(jbuildGroovyJar,
                groovydocToolClasspath.isBlank() ? groovyJar : groovydocToolClasspath);

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
