package jbuild.commands;

import java.util.Set;

public final class IncrementalChanges {
    public final Set<String> deletedClassFiles;
    public final Set<String> addedSourceFiles;

    public IncrementalChanges(Set<String> deletedClassFiles, Set<String> addedSourceFiles) {
        this.deletedClassFiles = deletedClassFiles;
        this.addedSourceFiles = addedSourceFiles;
    }
}
