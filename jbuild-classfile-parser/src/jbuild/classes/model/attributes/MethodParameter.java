package jbuild.classes.model.attributes;

import java.util.Arrays;

public final class MethodParameter {
    public enum AccessFlag {
        NONE((short) 0),
        ACC_FINAL((short) 0x0010),
        ACC_SYNTHETIC((short) 0x1000),
        ACC_MANDATED((short) 0x8000);

        public final short value;

        AccessFlag(short value) {
            this.value = value;
        }

        public static AccessFlag of(short value) {
            return Arrays.stream(AccessFlag.values())
                    .filter(it -> it.value == value)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Not a valid AccessFlag value: " + value));
        }
    }

    public final AccessFlag accessFlag;

    /**
     * The name of a method parameter.
     * <p>
     * It may be the empty String if the compiler did not include this attribute.
     */
    public final String name;

    public MethodParameter(AccessFlag accessFlag, String name) {
        this.accessFlag = accessFlag;
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MethodParameter that = (MethodParameter) o;

        if (accessFlag != that.accessFlag) return false;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        int result = accessFlag.hashCode();
        result = 31 * result + name.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "MethodParameter{" +
                "accessFlag=" + accessFlag +
                ", name=" + name +
                '}';
    }
}
