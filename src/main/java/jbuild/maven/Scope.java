package jbuild.maven;

import java.util.Locale;

public enum Scope {

    COMPILE, PROVIDED, RUNTIME, TEST, SYSTEM, IMPORT,

    ;

    /**
     * Check if this scope should include transitive dependencies with the given scope.
     *
     * @param other scope to check
     * @return true if this scope transitively includes dependencies with the given scope,
     * false otherwise
     */
    boolean includes(Scope other) {
        switch (this) {
            case COMPILE:
                return other == COMPILE;
            case RUNTIME:
            case TEST:
                return other == COMPILE || other == RUNTIME;
            case PROVIDED:
            case SYSTEM:
            case IMPORT:
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
