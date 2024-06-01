package jbuild.util;

import java.util.Locale;

/**
 * Environment variables and system properties provider.
 */
public final class Env {
    public static final int MAX_DEPENDENCY_TREE_DEPTH;
    public static final boolean IS_WINDOWS;

    static {
        MAX_DEPENDENCY_TREE_DEPTH = readInt("MAX_DEPENDENCY_TREE_DEPTH", 100);
        IS_WINDOWS = System.getProperty("user.home", "").toLowerCase(Locale.ROOT).contains("windows");
    }

    private static int readInt(String name, int defaultValue) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println("WARNING: environment variable " + name +
                    " does not have a valid integer value: " + value);
            return defaultValue;
        }
    }
}
