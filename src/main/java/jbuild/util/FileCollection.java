package jbuild.util;

import java.util.List;

public final class FileCollection {
    public final String directory;
    public final List<String> files;

    public FileCollection(String directory) {
        this(directory, List.of());
    }

    public FileCollection(String directory, List<String> files) {
        this.directory = directory;
        this.files = files;
    }
}
