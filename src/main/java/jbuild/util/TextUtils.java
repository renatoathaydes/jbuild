package jbuild.util;

public final class TextUtils {

    public static String firstNonBlank(String a, String b) {
        if (a == null || a.isBlank()) {
            return b;
        }
        return a;
    }

    public static boolean isEither(String toTest, String opt1, String opt2) {
        return opt1.equals(toTest) || opt2.equals(toTest);
    }

    public static String requireNonBlank(String value, String description) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(description + " cannot be blank");
        }
        return value;
    }
}
