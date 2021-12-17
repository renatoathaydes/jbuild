package jbuild.maven;

import java.util.EnumSet;
import java.util.Locale;

/**
 * A Maven dependency scope.
 */
public enum Scope {

    COMPILE, PROVIDED, RUNTIME, TEST, SYSTEM, IMPORT,

    ;

    /**
     * Expand a set of scopes so that the resulting set includes all transitive scopes.
     * <p>
     * This is useful to check if a scope is included in the returned Set without having to
     * check each item with {@link Scope#includes(Scope)}, which would be much less efficient.
     *
     * @param scopes original scopes
     * @return scopes and their transitive scopes
     */
    public static EnumSet<Scope> expandScopes(EnumSet<Scope> scopes) {
        var result = EnumSet.copyOf(scopes);
        for (var existingScope : values()) {
            for (var scope : scopes) {
                if (scope == existingScope) continue;
                if (scope.includes(existingScope)) {
                    result.add(existingScope);
                }
            }
        }
        return result;
    }

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
