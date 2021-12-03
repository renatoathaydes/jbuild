package jbuild.util;

public class TextUtils {

    public static String firstNonBlank(String a, String b) {
        if (a.isBlank()) {
            return b;
        }
        return a;
    }
}
