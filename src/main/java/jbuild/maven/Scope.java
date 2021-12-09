package jbuild.maven;

import java.util.Locale;

public enum Scope {

    COMPILE, PROVIDED, RUNTIME, TEST, SYSTEM, IMPORT,

    ;

    boolean includes(Scope other) {
        switch (this) {
            case COMPILE:
            case PROVIDED:
            case SYSTEM:
            case IMPORT:
                return this == other;
            case RUNTIME:
                return other == COMPILE || other == RUNTIME;
            case TEST:
                return other == COMPILE || other == RUNTIME || other == TEST;
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
