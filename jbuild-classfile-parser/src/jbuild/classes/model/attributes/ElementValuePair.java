package jbuild.classes.model.attributes;

public class ElementValuePair {

    public enum Type {
        BYTE('B'), CHAR('C'), DOUBLE('D'), FLOAT('F'), INT('I'), LONG('J'), SHORT('S'), BOOL('Z'),
        STRING('s'), ENUM('e'), CLASS('c'), ANNOTATION('@'), ARRAY('[');

        final char tag;

        Type(char tag) {
            this.tag = tag;
        }

        public static Type from(char c) {
            for (Type value : values()) {
                if (value.tag == c) return value;
            }
            throw new IllegalArgumentException("Not a valid Type: '" + c + "'");
        }
    }

    public final String name;
    public final Type type;

    /**
     * The value of an element-value pair.
     * <p>
     * This may be:
     * <ul>
     *     <li>An {@link EnumValue} in case the value is an enum value.</li>
     *     <li>A {@link AnnotationInfo} in case the value is an annotation.</li>
     *     <li>If the tag is {@link Type#CLASS}, a {@link String} with the name of the class.</li>
     *     <li>A {@link java.util.List} of values as described here.</li>
     *     <li>A constant value with the appropriate Java type (String, int, boolean, etc.) otherwise.</li>
     * </ul>
     */
    public final Object value;

    public ElementValuePair(String name, Type type, Object value) {
        this.name = name;
        this.type = type;
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ElementValuePair that = (ElementValuePair) o;

        if (!name.equals(that.name)) return false;
        if (type != that.type) return false;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + value.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ElementValuePair{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", value=" + value +
                '}';
    }
}
