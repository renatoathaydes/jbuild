package jbuild.java;

import jbuild.java.code.Code;
import jbuild.java.code.Definition;

import java.util.Objects;
import java.util.Optional;

public final class CodeReference {

    public final String jar;
    public final String type;
    public final Code to;
    private final Definition definition;

    public CodeReference(String jar,
                         String type,
                         Definition definition,
                         Code to) {
        this.jar = jar;
        this.type = type;
        this.definition = definition;
        this.to = to;
    }

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
                "jar='" + jar + '\'' +
                ", type=" + type +
                ", from=" + definition +
                ", to=" + to +
                '}';
    }
}
