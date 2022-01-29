package jbuild.java;

import java.io.File;
import java.util.Set;

public final class TestHelper {

    public static File file(String path) {
        return new File(path);
    }

    public static Jar jar(String path) {
        return jar(file(path));
    }

    public static Jar jar(File file) {
        return new Jar(file, Set.of(), () -> {
            throw new UnsupportedOperationException("cannot load test jar");
        });
    }
}
