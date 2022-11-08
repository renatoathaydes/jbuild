package jbuild.util;

import java.time.Duration;
import java.util.function.Supplier;

public final class TextUtils {

    public static final String LINE_END = System.lineSeparator();

    public static String firstNonBlank(String a, String b) {
        if (a == null || a.isBlank()) {
            return b;
        }
        return a;
    }

    public static String firstNonBlank(String a, Supplier<String> b) {
        if (a == null || a.isBlank()) {
            return b.get();
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

    public static CharSequence durationText(Duration duration) {
        if (duration.compareTo(Duration.ofDays(1)) >= 0) {
            return duration.toString();
        }
        var components = new long[]{
                duration.toHoursPart(),
                duration.toMinutesPart(),
                duration.toSecondsPart(),
                duration.toMillisPart()
        };
        var descriptions = new String[]{"hr", "min", "sec", "ms"};
        var text = new StringBuilder();
        for (int i = 0; i < components.length; i++) {
            var c = components[i];
            if (c > 0) {
                if (isAnyPrevGreaterThanZero(components, i)) text.append(", ");
                text.append(c).append(' ').append(descriptions[i]);
            }
        }
        if (text.length() == 0) return "0 ms";
        return text;
    }

    private static boolean isAnyPrevGreaterThanZero(long[] values, int index) {
        for (int i = 0; i < index; i++) {
            if (values[i] > 0) return true;
        }
        return false;
    }

    public static String trimStart(String text, char startChar) {
        if (text.isEmpty()) return "";
        int index = 0;
        while (index < text.length()) {
            if (text.charAt(index) == startChar) {
                index++;
            } else {
                break;
            }
        }
        return text.substring(index);
    }

    public static String trimEnd(String text, char endChar) {
        if (text.isEmpty()) return "";
        int index = text.length() - 1;
        while (index >= 0) {
            if (text.charAt(index) == endChar) {
                index--;
            } else {
                break;
            }
        }
        if (index < 0) return "";
        return text.substring(0, index + 1);
    }

    public static String unquote(String text) {
        if (text.isEmpty() || text.charAt(0) != '"') {
            return text;
        }
        var end = text.charAt(text.length() - 1) == '"' ? text.length() - 1 : text.length();
        return text.substring(1, end);
    }

    public static boolean isHttp(String address) {
        return address.startsWith("http://") || address.startsWith("https://");
    }
}
