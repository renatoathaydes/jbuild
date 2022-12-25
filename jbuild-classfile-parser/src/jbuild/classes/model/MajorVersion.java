package jbuild.classes.model;

import java.util.Optional;

public final class MajorVersion {
    public enum Known {

        V_1_1((short) 45), V_1_2((short) 46), V_1_3((short) 47), V_1_4((short) 48), V_5((short) 49),
        V_6((short) 50), V_7((short) 51), V_8((short) 52), V_9((short) 53), V_10((short) 54),
        V_11((short) 55), V_12((short) 56), V_13((short) 57), V_14((short) 58), V_15((short) 59),
        V_16((short) 60), V_17((short) 61), V_18((short) 62), V_19((short) 63),
        ;

        public final short value;

        private Known(short value) {
            this.value = value;
        }
    }

    public final short value;
    private final Known known;

    public MajorVersion(short value) {
        this.value = value;
        this.known = getKnown(value);
    }

    private static Known getKnown(short value) {
        for (var v : Known.values()) {
            if (v.value == value) {
                return v;
            }
        }
        return null;
    }

    public Optional<Known> toKnownVersion() {
        return Optional.ofNullable(known);
    }

    @Override
    public String toString() {
        if (known == null)
            return "MajorVersion{" + value + '}';
        return "MajorVersion{" + known + '}';
    }

}
