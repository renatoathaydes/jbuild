package jbuild.java;

import jbuild.util.Describable;

import java.io.File;

import static jbuild.util.JavaTypeUtils.typeNameToClassName;

public final class TypeReference implements Describable, Comparable<TypeReference> {

    public final File jar;
    public final Describable from;
    public final String typeTo;

    public TypeReference(File jar, Describable from, String typeTo) {
        this.jar = jar;
        this.from = from;
        this.typeTo = typeTo;
    }

    @Override
    public void describe(StringBuilder builder, boolean verbose) {
        builder.append(jar.getName()).append('!');
        from.describe(builder, verbose);
        builder.append(" -> ").append(typeNameToClassName(typeTo));
    }

    @Override
    public int compareTo(TypeReference other) {
        var result = jar.getName().compareTo(other.jar.getName());
        if (result == 0) {
            result = from.getDescription().compareTo(other.from.getDescription());
        }
        if (result == 0) {
            result = typeTo.compareTo(other.typeTo);
        }
        return result;
    }

    @Override
    public String toString() {
        return "TypeReference{" +
                "jar=" + jar +
                ", from='" + from + '\'' +
                ", typeTo=" + typeTo +
                '}';
    }
}
