package jbuild.java.code;

public final class MethodDefinition {

    public final String name;
    public final String type;

    public MethodDefinition(String name, String type) {
        this.name = name;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MethodDefinition that = (MethodDefinition) o;

        if (!name.equals(that.name)) return false;
        return type.equals(that.type);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + type.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "MethodDefinition{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
