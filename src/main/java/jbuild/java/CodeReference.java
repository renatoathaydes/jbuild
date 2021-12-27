package jbuild.java;

import jbuild.java.code.Code;
import jbuild.java.code.MethodDefinition;

import java.util.Objects;
import java.util.Optional;

public final class CodeReference {

    public final String jar;
    public final String type;
    public final Code to;
    private final MethodDefinition method;

    public CodeReference(String jar,
                         String type,
                         MethodDefinition method,
                         Code to) {
        this.jar = jar;
        this.type = type;
        this.method = method;
        this.to = to;
    }

    public Optional<MethodDefinition> getMethod() {
        return Optional.ofNullable(method);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CodeReference that = (CodeReference) o;

        if (!jar.equals(that.jar)) return false;
        if (!type.equals(that.type)) return false;
        if (!to.equals(that.to)) return false;
        return Objects.equals(method, that.method);
    }

    @Override
    public int hashCode() {
        int result = jar.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + to.hashCode();
        result = 31 * result + (method != null ? method.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "CodeReference{" +
                "jar='" + jar + '\'' +
                ", type=" + type +
                ", method=" + method +
                ", to=" + to +
                '}';
    }
}
