package jbuild.api.config;

public final class DependencySpec {
    public final boolean transitive;
    public final DependencyScope scope;
    public final String path;

    public static final DependencySpec DEFAULT = new DependencySpec(true, DependencyScope.ALL, "");

    public DependencySpec(boolean transitive, DependencyScope scope, String path) {
        this.transitive = transitive;
        this.scope = scope;
        this.path = path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DependencySpec that = (DependencySpec) o;

        if (transitive != that.transitive) return false;
        if (scope != that.scope) return false;
        return path.equals(that.path);
    }

    @Override
    public int hashCode() {
        int result = (transitive ? 1 : 0);
        result = 31 * result + scope.hashCode();
        result = 31 * result + path.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "DependencySpec{" +
                "transitive=" + transitive +
                ", scope=" + scope +
                ", path='" + path + '\'' +
                '}';
    }
}
