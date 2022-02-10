package jbuild.util;

import java.io.File;
import java.io.FileFilter;

public final class JarFileFilter implements FileFilter {
    private static final JarFileFilter INSTANCE;

    static {
        INSTANCE = new JarFileFilter();
    }

    public static JarFileFilter getInstance() {
        return INSTANCE;
    }

    private JarFileFilter() {
    }

    @Override
    public boolean accept(File file) {
        if (!file.isFile()) return false;
        return file.getName().endsWith(".jar");
    }

}
