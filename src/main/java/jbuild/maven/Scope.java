package jbuild.maven;

import java.util.EnumSet;
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

    public EnumSet<Scope> transitiveScopes() {
        switch (this) {
            case COMPILE:
                return EnumSet.of(COMPILE);
            case RUNTIME:
            case TEST:
                return EnumSet.of(COMPILE, RUNTIME);
            case PROVIDED:
            case SYSTEM:
            case IMPORT:
            default:
                return EnumSet.noneOf(Scope.class);
        }
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
