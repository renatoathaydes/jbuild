package jbuild.api.change;

/**
 * A file or directory change.
 * <p>
 * If {@code path} is a directory, then a change was detected in the contents of the directory.
 * If {@code path} is a file, then the change applies only to the file itself.
 */
public final class FileChange {
    public final String path;
    public final ChangeKind kind;

    public FileChange(String path, ChangeKind kind) {
        this.path = path;
        this.kind = kind;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileChange that = (FileChange) o;

        if (!path.equals(that.path)) return false;
        return kind == that.kind;
    }

    @Override
    public int hashCode() {
        int result = path.hashCode();
        result = 31 * result + kind.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "FileChange{" +
                "path='" + path + '\'' +
                ", kind=" + kind +
                '}';
    }
}
