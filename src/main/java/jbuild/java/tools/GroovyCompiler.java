package jbuild.java.tools;

import jbuild.api.JBuildException;
import jbuild.commands.JbuildCompiler;
import jbuild.log.JBuildLog;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static jbuild.api.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.java.tools.Tools.Javac.collectArgs;

public final class GroovyCompiler implements JbuildCompiler {

    public static final String GROOVYC_CLASS = "org.codehaus.groovy.tools.FileSystemCompiler";
    public static final String GROOVY_CLASS_LOADER = "org.codehaus.groovy.tools.RootLoader";

    private final JBuildLog log;
    private final String groovyJar;

    public GroovyCompiler(JBuildLog log, String groovyJar) {
        this.log = log;
        this.groovyJar = groovyJar;
    }

    @Override
    public ToolRunResult compile(Set<String> sourceFiles,
                                 String outDir,
                                 String classPath,
                                 String modulePath,
                                 List<String> compilerArgs) {
        // groovy does not have the --module-path option
        var args = collectArgs(sourceFiles, outDir, joinClasspath(classPath, modulePath), "", compilerArgs, true);
        return run(args);
    }

    private <CL extends ClassLoader & Closeable> ToolRunResult run(List<String> args) {
        CL groovyClassLoader;
        URL[] classpath;
        try {
            classpath = new URL[]{Paths.get(groovyJar).toUri().toURL()};
        } catch (MalformedURLException e) {
            throw new JBuildException("Could not create Groovy compiler ClassLoader from " + groovyJar +
                    " due to: " + e, ACTION_ERROR);
        }

        try {
            groovyClassLoader = createGroovyClassLoader(classpath);
        } catch (Exception e) {
            throw new JBuildException("Could not create Groovy compiler ClassLoader due to: " + e, ACTION_ERROR);
        }

        var compilationFuture = new CompletableFuture<ToolRunResult>();

        new Thread(() -> {
            Thread.currentThread().setContextClassLoader(groovyClassLoader);
            try (groovyClassLoader) {
                compilationFuture.complete(compile(groovyClassLoader, args.toArray(new String[0])));
            } catch (Throwable t) {
                compilationFuture.completeExceptionally(t);
            }
        }).start();

        try {
            return compilationFuture.whenComplete((result, error) -> {
                try {
                    groovyClassLoader.close();
                } catch (IOException e) {
                    log.verbosePrintln(() -> "Problem closing Groovy ClassLoader: " + e);
                }
            }).get();
        } catch (ExecutionException e) {
            throw new JBuildException("Could not compile due to: " + e.getCause(), ACTION_ERROR);
        } catch (Exception e) {
            throw new JBuildException("Could not compile due to: " + e, ACTION_ERROR);
        }
    }

    private <CL extends ClassLoader & Closeable> MemoryToolRunResult compile(CL groovyClassLoader, String[] args) {
        Class<?> groovycClass;
        Method method;
        try (groovyClassLoader) {
            try {
                groovycClass = groovyClassLoader.loadClass(GROOVYC_CLASS);
            } catch (ClassNotFoundException e) {
                throw new JBuildException("Could not load Groovy compiler (" + GROOVYC_CLASS + ") due to: " + e,
                        ACTION_ERROR);
            }
            try {
                method = groovycClass.getMethod("commandLineCompile", String[].class);
            } catch (NoSuchMethodException e) {
                throw new JBuildException("Could not find method " + GROOVYC_CLASS + "#commandLineCompile " +
                        "for compiling Groovy code", ACTION_ERROR);
            }

            try {
                method.invoke(null, (Object) args);
            } catch (InvocationTargetException e) {
                return new MemoryToolRunResult(1, args, "", e.getCause().toString());
            } catch (Exception e) {
                throw new JBuildException("java process failed due to: " + e, ACTION_ERROR);
            }
        } catch (IOException e) {
            // ignore, this only happens if the ClassLoader couldn't be closed
            log.verbosePrintln(() -> "WARN: Could not close Groovy compiler ClassLoader due to: " + e);
        }

        return new MemoryToolRunResult(0, args, "", "");
    }

    @SuppressWarnings({"unchecked"})
    private static <CL extends ClassLoader & Closeable> CL createGroovyClassLoader(URL[] classpath)
            throws Exception {
        ClassLoader mainClassLoader = new URLClassLoader(classpath, ClassLoader.getPlatformClassLoader());
        var groovyClassLoaderClass = mainClassLoader.loadClass(GROOVY_CLASS_LOADER);
        var constructor = groovyClassLoaderClass.getDeclaredConstructor(URL[].class, ClassLoader.class);
        return (CL) constructor.newInstance(classpath, mainClassLoader);
    }

    private static String joinClasspath(String classPath, String modulePath) {
        if (classPath.isEmpty()) return modulePath;
        if (modulePath.isEmpty()) return classPath;
        return classPath + File.pathSeparator + modulePath;
    }
}
