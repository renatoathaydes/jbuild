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
}
