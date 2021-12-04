package jbuild.util;

public class TextUtils {

    public static String firstNonBlank(String a, String b) {
        if (a.isBlank()) {
            return b;
        }
        return a;
    }

    public static boolean isEither(String toTest, String opt1, String opt2) {
        return opt1.equals(toTest) || opt2.equals(toTest);
    }
}
