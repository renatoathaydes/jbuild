package jbuild.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class JarPatcher {
    private JarPatcher() {
    }

    public static void patchJar(File jarFile,
                                String baseDir,
                                Set<String> add,
                                Set<String> delete) throws IOException {
        if (add.isEmpty() && delete.isEmpty()) return;
        var newFile = Files.createTempFile("jbuild-temp-jar-", ".jar").toFile();
        var newJar = new ZipOutputStream(new FileOutputStream(newFile));

        try (var jar = new ZipFile(jarFile, ZipFile.OPEN_READ)) {
            var entriesToAdd = new TreeSet<>(new EntryComparator());
            EntryToAdd metaInfToAdd = null, manifestToAdd = null;
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (delete.contains(entry.getName())) continue;
                if (metaInfToAdd == null && entry.getName().equals("META-INF/")) {
                    metaInfToAdd = new EntryToAdd(entry, true);
                } else if (manifestToAdd == null && entry.getName().equals("META-INF/MANIFEST.MF")) {
                    manifestToAdd = new EntryToAdd(entry, true);
                } else {
                    entriesToAdd.add(new EntryToAdd(entry, true));
                }
            }
            removeEmptyDirectories(entriesToAdd);
            for (var toAdd : add) {
                if (metaInfToAdd == null && toAdd.equals("META-INF/")) {
                    metaInfToAdd = new EntryToAdd(new ZipEntry(toAdd), false);
                } else if (manifestToAdd == null && toAdd.equals("META-INF/MANIFEST.MF")) {
                    manifestToAdd = new EntryToAdd(new ZipEntry(toAdd), false);
                } else {
                    addDirectoryEntriesFor(toAdd, entriesToAdd);
                    entriesToAdd.add(new EntryToAdd(new ZipEntry(toAdd), false));
                }
            }
            if (metaInfToAdd != null) metaInfToAdd.addFrom(jar, newJar, baseDir);
            if (manifestToAdd != null) manifestToAdd.addFrom(jar, newJar, baseDir);
            for (var entry : entriesToAdd) entry.addFrom(jar, newJar, baseDir);
        }
        newJar.close();
        if (!newFile.renameTo(jarFile)) {
            throw new IOException("unable to replace jar file with new contents: " + jarFile);
        }
    }

    private static void removeEmptyDirectories(TreeSet<EntryToAdd> entriesToAdd) {
        var iter = new RemovalIterator(entriesToAdd);
        String previousDir = null;
        while (iter.hasNext()) {
            var entry = iter.next().entry;
            var isDir = entry.isDirectory();
            if (previousDir != null && isDir && !entry.getName().startsWith(previousDir)) {
                iter.removePrevious();
            }
            previousDir = isDir ? entry.getName() : null;
        }
    }

    private static void addDirectoryEntriesFor(String path, TreeSet<EntryToAdd> entriesToAdd) {
        var index = 1;
        while (true) {
            var pathIndex = path.indexOf('/', index);
            if (pathIndex > 0) {
                entriesToAdd.add(new EntryToAdd(new ZipEntry(path.substring(0, pathIndex + 1)), false));
                index = pathIndex + 2;
            } else {
                break;
            }
        }
    }

    private static void addZipEntryToJar(ZipOutputStream newJar, ZipFile sourceJar, ZipEntry entry) throws IOException {
        newJar.putNextEntry(entry);
        var entryStream = sourceJar.getInputStream(entry);
        entryStream.transferTo(newJar);
        entryStream.close();
        newJar.closeEntry();
    }

    private static void addZipEntryToJar(String baseDir, ZipOutputStream newJar, ZipEntry toAdd) throws IOException {
        if (toAdd.isDirectory()) {
            newJar.putNextEntry(toAdd);
            newJar.closeEntry();
        } else {
            var file = new File(baseDir, toAdd.getName());
            try (var in = new FileInputStream(file)) {
                newJar.putNextEntry(toAdd);
                in.transferTo(newJar);
                newJar.closeEntry();
            }
        }
    }

    private static final class EntryToAdd {
        final ZipEntry entry;
        final boolean fromJar;

        EntryToAdd(ZipEntry entry, boolean fromJar) {
            this.entry = entry;
            this.fromJar = fromJar;
        }

        void addFrom(ZipFile jar, ZipOutputStream newJar, String baseDir) throws IOException {
            if (fromJar) {
                addZipEntryToJar(newJar, jar, entry);
            } else {
                addZipEntryToJar(baseDir, newJar, entry);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            EntryToAdd that = (EntryToAdd) o;

            return entry.equals(that.entry);
        }

        @Override
        public int hashCode() {
            return entry.hashCode();
        }

        @Override
        public String toString() {
            return "EntryToAdd{" +
                    "entry=" + entry.getName() +
                    ", fromJar=" + fromJar +
                    '}';
        }
    }

    private static final class RemovalIterator implements Iterator<EntryToAdd> {

        private boolean isFirstItem = true;
        private final Iterator<EntryToAdd> delegate;
        private final Iterator<EntryToAdd> delegateOneItemBehind;

        public RemovalIterator(Collection<EntryToAdd> collection) {
            this.delegate = collection.iterator();
            this.delegateOneItemBehind = collection.iterator();
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public EntryToAdd next() {
            var item = delegate.next();
            if (isFirstItem) {
                isFirstItem = false;
            } else {
                delegateOneItemBehind.next();
            }
            return item;
        }

        void removePrevious() {
            delegateOneItemBehind.remove();
        }
    }

    private static final class EntryComparator implements Comparator<EntryToAdd> {
        @Override
        public int compare(EntryToAdd e1, EntryToAdd e2) {
            var name1 = e1.entry.getName();
            var name2 = e2.entry.getName();
            return name1.compareToIgnoreCase(name2);
        }
    }

}
