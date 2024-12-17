package jbuild.api.config;

public enum DependencyScope {
    /**
     * dependency is required both at compile-time and runtime.
     */
    ALL,

    /**
     * dependency is required at compile-time, but not runtime.
     */
    COMPILE_ONLY,

    /**
     * dependency is required at runtime, but not compile-time.
     */
    RUNTIME_ONLY,

    ;

    public static DependencyScope fromString(String str) {
        switch (str) {
            case "all":
            case "":
                return ALL;
            case "compile-only":
                return COMPILE_ONLY;
            case "runtime-only":
                return RUNTIME_ONLY;
            default:
                throw new IllegalArgumentException("Invalid dependency scope: '" + str + '\'');
        }
    }
}
