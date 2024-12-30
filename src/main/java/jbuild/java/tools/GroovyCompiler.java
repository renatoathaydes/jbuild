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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static jbuild.api.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.api.JBuildException.ErrorCause.IO_WRITE;

public final class GroovyCompiler implements JbuildCompiler {

    public static final String GROOVY_STARTER_CLASS = "org.codehaus.groovy.tools.GroovyStarter";
    public static final String GROOVYC_CLASS = "org.codehaus.groovy.tools.FileSystemCompiler";

    private final JBuildLog log;
    private final String groovyJar;

    public GroovyCompiler(JBuildLog log, String groovyJar) {
        this.log = log;
        this.groovyJar = groovyJar;
    }

    @Override
    public ToolRunResult compile(Set<String> sourceFiles, String outDir, String classpath, List<String> compilerArgs) {
        Path confFileDir;
        try {
            confFileDir = Files.createTempDirectory("groovyStarter");
        } catch (IOException e) {
            throw new JBuildException("Could not create temp dir for Groovy compilation due to " + e, IO_WRITE);
        }
        var groovyConfFile = confFileDir.resolve("groovy.conf");
        try (var stream = requireNonNull(getClass().getResourceAsStream("/groovyStarter.conf"))) {
            Files.copy(stream, groovyConfFile);
        } catch (IOException e) {
            throw new JBuildException("Could not copy config for Groovy compilation due to " + e, IO_WRITE);
        }
        String[] allArgs = new String[8 + compilerArgs.size() + sourceFiles.size()];
        allArgs[0] = "--main";
        allArgs[1] = GROOVYC_CLASS;
        allArgs[2] = "--conf";
        allArgs[3] = groovyConfFile.toString();
        allArgs[4] = "--classpath";
        allArgs[5] = classpath;
        allArgs[6] = "-d";
        allArgs[7] = outDir;
        int index = 8;
        for (String arg : compilerArgs) {
            allArgs[index++] = arg;
        }
        for (String sourceFile : sourceFiles) {
            allArgs[index++] = sourceFile;
        }

        return run(allArgs);
    }

    private ToolRunResult run(String[] args) {
        URLClassLoader groovyClassLoader;
        try {
            groovyClassLoader = new URLClassLoader(new URL[]{Paths.get(groovyJar).toUri().toURL()});
        } catch (MalformedURLException e) {
            throw new JBuildException("Could not create Groovy compiler ClassLoader from " + groovyJar +
                    " due to: " + e, ACTION_ERROR);
        }
        Class<?> groovyStarterClass;
        Method method;
        try (groovyClassLoader) {
            try {
                groovyStarterClass = groovyClassLoader.loadClass(GROOVY_STARTER_CLASS);
            } catch (ClassNotFoundException e) {
                throw new JBuildException("Could not load Groovy compiler (" + GROOVY_STARTER_CLASS + ") due to: " + e,
                        ACTION_ERROR);
            }
            try {
                method = groovyStarterClass.getMethod("rootLoader", String[].class);
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
}
