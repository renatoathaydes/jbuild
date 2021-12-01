package jbuild.util;

import java.io.File;

public final class FileUtils {
    public static boolean ensureDirectoryExists(File dir) {
        if (dir.isDirectory() || dir.mkdirs()) {
            return true;
        }
        return false;
    }
}
