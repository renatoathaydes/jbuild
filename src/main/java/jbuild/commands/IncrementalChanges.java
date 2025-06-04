package jbuild.commands;

import jbuild.util.Either;
import jbuild.util.FileUtils;

import java.util.Set;

public final class IncrementalChanges {
    public final Set<String> deletedFiles;
    public final Set<String> addedFiles;

    public IncrementalChanges(Set<String> deletedFiles, Set<String> addedFiles) {
        this.deletedFiles = deletedFiles;
        this.addedFiles = addedFiles;
    }

    public IncrementalChanges relativize(String dir, Either<String, String> outputDirOrJar) {
        var outputDir = outputDirOrJar.map(outDir -> FileUtils.relativize(dir, outDir), jar -> null);
        return new IncrementalChanges(
                // When source files are deleted, jb computes which class files originate from those sources
                // and sends the class file paths here... hence, we need to relativize class files to the
                // outputDir if necessary. If the output is a jar, it does not require relativization.
                outputDir == null ? deletedFiles : FileUtils.relativize(outputDir, deletedFiles),
                // added files are always relative
                FileUtils.relativize(dir, addedFiles));
    }
}
