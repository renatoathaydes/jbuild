package jbuild.maven;

import java.util.Locale;

public enum Scope {

    COMPILE, PROVIDED, RUNTIME, TEST, SYSTEM, IMPORT,

    ;

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
