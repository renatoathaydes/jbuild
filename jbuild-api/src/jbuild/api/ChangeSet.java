package jbuild.api;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

public final class ChangeSet {
    private final FileChange[] inputChanges;
    private final FileChange[] outputChanges;

    public ChangeSet(FileChange[] inputChanges, FileChange[] outputChanges) {
        this.inputChanges = inputChanges;
        this.outputChanges = outputChanges;
    }

    public Iterable<FileChange> getInputChanges() {
        return asIterable(inputChanges);
    }

    public Iterable<FileChange> getOutputChanges() {
        return asIterable(outputChanges);
    }

    private static Iterable<FileChange> asIterable(FileChange[] changes) {
        return () -> new Iterator<>() {
            int index = 0;

            @Override
            public boolean hasNext() {
                return index < changes.length;
            }

            @Override
            public FileChange next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return changes[index++];
            }
        };
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;

        var changeSet = (ChangeSet) other;

        return Arrays.equals(inputChanges, changeSet.inputChanges)
                && Arrays.equals(outputChanges, changeSet.outputChanges);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(inputChanges);
        result = 31 * result + Arrays.hashCode(outputChanges);
        return result;
    }

    @Override
    public String toString() {
        return "ChangeSet{" +
                "inputChanges=" + Arrays.toString(inputChanges) +
                ", outputChanges=" + Arrays.toString(outputChanges) +
                '}';
    }
}
