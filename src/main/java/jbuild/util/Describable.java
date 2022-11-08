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

    static Describable of(Describable description1, Describable description2) {
        return (builder, verbose) -> {
            description1.describe(builder, verbose);
            builder.append('\n');
            description2.describe(builder, verbose);
        };
    }
}
