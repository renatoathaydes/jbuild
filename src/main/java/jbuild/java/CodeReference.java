package jbuild.java;

import jbuild.java.code.Code;
import jbuild.java.code.Definition;

import java.io.File;
import java.util.Objects;
import java.util.Optional;

/**
 * A reference from a certain type to another type or component of another type.
 */
public final class CodeReference {

    /**
     * The jar in which the code reference is found.
     */
    public final File jar;

    /**
     * The type in which the reference comes from.
     */
    public final String type;

    /**
     * The exact {@link Code} this reference points to.
     */
    public final Code to;

    private final Definition definition;

    public CodeReference(File jar,
                         String type,
                         Definition definition,
                         Code to) {
        this.jar = jar;
        this.type = type;
        this.definition = definition;
        this.to = to;
    }

    /**
     * @return the optional definition this reference originates from. It may not be known because it originates
     * from a synthetic method, for example.
     */
    public Optional<? extends Definition> getDefinition() {
        return Optional.ofNullable(definition);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CodeReference that = (CodeReference) o;

        if (!jar.equals(that.jar)) return false;
        if (!type.equals(that.type)) return false;
        if (!to.equals(that.to)) return false;
        return Objects.equals(definition, that.definition);
    }

    @Override
    public int hashCode() {
        int result = jar.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + to.hashCode();
        result = 31 * result + (definition != null ? definition.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "CodeReference{" +
                "jar='" + jar.getPath() + '\'' +
                ", type=" + type +
                ", from=" + definition +
                ", to=" + to +
                '}';
    }
}
