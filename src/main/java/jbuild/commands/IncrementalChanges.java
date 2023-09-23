package jbuild.commands;

import jbuild.util.FileUtils;

import java.util.Set;

public final class IncrementalChanges {
    public final Set<String> deletedFiles;
    public final Set<String> addedFiles;

    public IncrementalChanges(Set<String> deletedFiles, Set<String> addedFiles) {
        this.deletedFiles = deletedFiles;
        this.addedFiles = addedFiles;
    }

    public IncrementalChanges relativize(String dir) {
        return new IncrementalChanges(
                FileUtils.relativize(dir, deletedFiles),
                FileUtils.relativize(dir, addedFiles));
    }
}
