package jbuild.commands;

import java.util.Set;

public final class IncrementalChanges {
    public final Set<String> deletedFiles;
    public final Set<String> addedFiles;

    public IncrementalChanges(Set<String> deletedFiles, Set<String> addedFiles) {
        this.deletedFiles = deletedFiles;
        this.addedFiles = addedFiles;
    }
}
