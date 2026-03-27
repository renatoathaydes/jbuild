package jbuild.java;

import jbuild.api.JBuildException;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

import static jbuild.api.JBuildException.ErrorCause.USER_INPUT;

public final class ClassLoaderFactory {

    public static ClassLoader createClassLoader(String classpath, ClassLoader parent) {
        var parts = classpath.split(File.pathSeparator);
        var urls = new URL[parts.length];
        for (var i = 0; i < parts.length; i++) {
            var file = new File(parts[i]);
            try {
                urls[i] = file.toURI().toURL();
            } catch (MalformedURLException e) {
                throw new JBuildException("Invalid classpath URL: " + parts[i], USER_INPUT);
            }
        }
        System.out.println("Classpath: " + Arrays.toString(urls));
        return new URLClassLoader(urls, parent);
    }

}
