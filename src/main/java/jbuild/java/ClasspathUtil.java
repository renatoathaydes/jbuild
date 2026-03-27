package jbuild.java;

import java.io.File;

public final class ClasspathUtil {

    private ClasspathUtil() {
    }

    public static String joinClasspath(String classPath, String modulePath) {
        if (classPath.isEmpty()) return modulePath;
        if (modulePath.isEmpty()) return classPath;
        return classPath + File.pathSeparator + modulePath;
    }
}
