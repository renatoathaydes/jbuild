package jbuild.java.code;

import jbuild.java.JavaType;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

public final class TypeDefinition {

    public final JavaType type;
    public final String typeName;
    public final Set<String> implementedInterfaces;
    public final Set<Definition.FieldDefinition> fields;
    // method handles are "special" in that they don't show up directly in the bytecode where they are used,
    // but are only referenced from the constant table (see the "FunctionalCode" test class' "peek(log::debug)" ref).
    public final Set<Code.Method> methodHandles;
    public final Map<Definition.MethodDefinition, Set<Code>> methods;

    public TypeDefinition(JavaType type,
                          Set<Definition.FieldDefinition> fields,
                          Set<Code.Method> methodHandles,
                          Map<Definition.MethodDefinition, Set<Code>> methods) {
        this.type = type;
        this.fields = fields;
        this.methodHandles = methodHandles;
        this.methods = methods;

        this.typeName = type.name;
        this.implementedInterfaces = type.interfaces.stream()
                .map(bound -> bound.name)
                .collect(toSet());
    }

    public Optional<String> getExtendedType() {
        return type.superType.equals(JavaType.OBJECT)
                ? Optional.empty()
                : Optional.ofNullable(type.superType.name);
    }

    @Override
    public String toString() {
        return "TypeDefinition{" +
                "type='" + type + '\'' +
                ", fields=" + fields +
                ", methodHandles=" + methodHandles +
                ", methods=" + methods +
                '}';
    }
}
