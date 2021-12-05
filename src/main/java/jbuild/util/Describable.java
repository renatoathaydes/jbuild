package jbuild.util;

public interface Describable {

    void describe(StringBuilder builder, boolean verbose);

    default String getDescription() {
        var builder = new StringBuilder();
        describe(builder, false);
        return builder.toString();
    }

    static Describable of(String description) {
        return (builder, verbose) -> builder.append(description);
    }
}
