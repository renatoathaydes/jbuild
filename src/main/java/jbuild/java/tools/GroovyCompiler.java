package jbuild.java.tools;

import jbuild.api.JBuildException;
import jbuild.commands.JbuildCompiler;
import jbuild.log.JBuildLog;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static jbuild.api.JBuildException.ErrorCause.ACTION_ERROR;

public final class GroovyCompiler implements JbuildCompiler {

    public static final String GROOVYC_CLASS = "org.codehaus.groovy.tools.FileSystemCompiler";

    private final JBuildLog log;
    private final String groovyJar;

    public GroovyCompiler(JBuildLog log, String groovyJar) {
        this.log = log;
        this.groovyJar = groovyJar;
    }

    @Override
    public ToolRunResult compile(Set<String> sourceFiles, String outDir, String classpath, List<String> compilerArgs) {
        String[] allArgs = new String[4 + compilerArgs.size() + sourceFiles.size()];
        allArgs[0] = "-d";
        allArgs[1] = outDir;
        allArgs[2] = "-cp";
        allArgs[3] = classpath;
        int index = 4;
        for (String arg : compilerArgs) {
            allArgs[index++] = arg;
        }
        for (String sourceFile : sourceFiles) {
            allArgs[index++] = sourceFile;
        }

        run(allArgs);

        return new MemoryToolRunResult(0, allArgs, "", "");
    }

    private void run(String[] args) {
        URLClassLoader groovyClassLoader;
        try {
            groovyClassLoader = new URLClassLoader(new URL[]{Paths.get(groovyJar).toUri().toURL()});
        } catch (MalformedURLException e) {
            throw new JBuildException("Could not create Groovy compiler ClassLoader from " + groovyJar +
                    " due to: " + e, ACTION_ERROR);
        }
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
                throw new JBuildException("Groovy compiler failed due to: " + e.getCause(), ACTION_ERROR);
            } catch (Exception e) {
                throw new JBuildException("java process failed due to: " + e, ACTION_ERROR);
            }
        } catch (IOException e) {
            // ignore, this only happens if the ClassLoader couldn't be closed
            log.verbosePrintln(() -> "WARN: Could not close Groovy compiler ClassLoader due to: " + e);
        }
    }
}
