package jbuild.java.code;

public final class FieldDefinition extends Definition {

    public FieldDefinition(String name, String type) {
        super(name, type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FieldDefinition that = (FieldDefinition) o;

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
        return "FieldDefinition{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
