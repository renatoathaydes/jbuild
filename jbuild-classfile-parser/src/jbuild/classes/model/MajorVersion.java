package jbuild.classes.model;

import java.util.Optional;

public final class MajorVersion {
    public enum Known {

        V_1_1((short) 45), V_1_2((short) 46), V_1_3((short) 47), V_1_4((short) 48), V_5((short) 49),
        V_6((short) 50), V_7((short) 51), V_8((short) 52), V_9((short) 53), V_10((short) 54),
        V_11((short) 55), V_12((short) 56), V_13((short) 57), V_14((short) 58), V_15((short) 59),
        V_16((short) 60), V_17((short) 61), V_18((short) 62), V_19((short) 63),
        V_20((short) 64), V_21((short) 65), V_22((short) 66), V_23((short) 67),
        V_24((short) 68), V_25((short) 69), V_26((short) 70), V_27((short) 71), V_28((short) 72),
        V_29((short) 73), V_30((short) 74), V_31((short) 75),
        V_32((short) 76), V_33((short) 77), V_34((short) 78),
        V_35((short) 79), V_36((short) 80), V_37((short) 81),
        V_38((short) 82), V_39((short) 83), V_40((short) 84),
        ;

        public final short value;

        Known(short value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return "MajorVersion.Known{" +
                    "value=" + value +
                    '}';
        }

        public String displayName() {
            switch (this) {
                case V_1_1:
                    return "1.1";
                case V_1_2:
                    return "1.2";
                case V_1_3:
                    return "1.3";
                case V_1_4:
                    return "1.4";
                case V_5:
                    return "1.5";
                case V_6:
                    return "6";
                case V_7:
                    return "7";
                case V_8:
                    return "8";
                case V_9:
                    return "9";
                case V_10:
                    return "10";
                case V_11:
                    return "11";
                case V_12:
                    return "12";
                case V_13:
                    return "13";
                case V_14:
                    return "14";
                case V_15:
                    return "15";
                case V_16:
                    return "16";
                case V_17:
                    return "17";
                case V_18:
                    return "18";
                case V_19:
                    return "19";
                case V_20:
                    return "20";
                case V_21:
                    return "21";
                case V_22:
                    return "22";
                case V_23:
                    return "23";
                case V_24:
                    return "24";
                case V_25:
                    return "25";
                case V_26:
                    return "26";
                case V_27:
                    return "27";
                case V_28:
                    return "28";
                case V_29:
                    return "29";
                case V_30:
                    return "30";
                case V_31:
                    return "31";
                case V_32:
                    return "32";
                case V_33:
                    return "33";
                case V_34:
                    return "34";
                case V_35:
                    return "35";
                case V_36:
                    return "36";
                case V_37:
                    return "37";
                case V_38:
                    return "38";
                case V_39:
                    return "39";
                case V_40:
                    return "40";
                default:
                    return "Major value";
            }
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
