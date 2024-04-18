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
}
