package jbuild.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class JarPatcher {
    private JarPatcher() {
    }

    public static void deleteFromJar(File jarFile,
                                     Set<String> filesToDelete) throws IOException {
        if (filesToDelete.isEmpty()) return;
        var newFile = Files.createTempFile("jbuild-temp-jar-", ".jar").toFile();

        try (var jar = new ZipFile(jarFile, ZipFile.OPEN_READ);
             var newJar = new ZipOutputStream(new FileOutputStream(newFile))) {
            var entriesToAdd = new ArrayList<ZipEntry>(jar.size());
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!filesToDelete.contains(entry.getName())) {
                    entriesToAdd.add(entry);
                }
            }
            removeEmptyDirectories(entriesToAdd);
            for (var entry : entriesToAdd) {
                newJar.putNextEntry(entry);
                try (var in = jar.getInputStream(entry)) {
                    in.transferTo(newJar);
                    newJar.closeEntry();
                }
            }
        }
        Files.move(newFile.toPath(), jarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void removeEmptyDirectories(List<ZipEntry> entries) {
        var emptyDirs = new ArrayList<DirWithIndex>();
        var previousEntryWasFile = true;
        for (var i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            var isDir = entry.isDirectory();
            if (isDir) {
                emptyDirs.add(new DirWithIndex(entry.getName(), i));
            } else if (!previousEntryWasFile) { // only do this on the first file of each directory
                removeParentDirsFrom(emptyDirs, entry.getName());
            }
            previousEntryWasFile = !isDir;
        }
        emptyDirs.stream()
                .sorted(Comparator.comparing((DirWithIndex e) -> e.index).reversed())
                .forEach(e -> entries.remove(e.index));
    }

    private static void removeParentDirsFrom(List<DirWithIndex> emptyDirs, String name) {
        while (true) {
            var dirEndIndex = name.lastIndexOf('/');
            if (dirEndIndex > 0) {
                var dir = name.substring(0, dirEndIndex + 1);
                emptyDirs.remove(new DirWithIndex(dir, 0));
                name = name.substring(0, dirEndIndex);
            } else {
                break;
            }
        }
    }

    private static final class DirWithIndex {
        final String dir;
        final int index;

        public DirWithIndex(String dir, int index) {
            this.dir = dir;
            this.index = index;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            var that = (DirWithIndex) other;
            return dir.equals(that.dir);
        }

        @Override
        public int hashCode() {
            return dir.hashCode();
        }
    }
}
