package jbuild.classes.model.attributes;

public final class EnumValue {
    public final String typeName;
    public final String constName;

    public EnumValue(String typeName, String constName) {
        this.typeName = typeName;
        this.constName = constName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EnumValue enumValue = (EnumValue) o;

        if (!typeName.equals(enumValue.typeName)) return false;
        return constName.equals(enumValue.constName);
    }

    @Override
    public int hashCode() {
        int result = typeName.hashCode();
        result = 31 * result + constName.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "EnumValue{" +
                "typeName='" + typeName + '\'' +
                ", constName='" + constName + '\'' +
                '}';
    }
}
