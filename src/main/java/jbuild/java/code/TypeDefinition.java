package jbuild.java.code;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TypeDefinition {

    private final String extended;

    public final Set<String> implementedInterfaces;
    public final String typeName;
    public final Set<FieldDefinition> fields;
    // method handles are "special" in that they don't show up directly in the bytecode where they are used,
    // but are only referenced from the constant table (see the "FunctionalCode" test class' "peek(log::debug)" ref).
    public final Set<Code.Method> methodHandles;
    public final Map<MethodDefinition, Set<Code>> methods;

    public TypeDefinition(String typeName,
                          String extended,
                          Set<String> implementedInterfaces,
                          Set<FieldDefinition> fields,
                          Set<Code.Method> methodHandles,
                          Map<MethodDefinition, Set<Code>> methods) {
        this.typeName = typeName;
        this.extended = extended;
        this.implementedInterfaces = implementedInterfaces;
        this.fields = fields;
        this.methodHandles = methodHandles;
        this.methods = methods;
    }

    public Optional<String> getExtendedType() {
        return Optional.ofNullable(extended);
    }

    @Override
    public String toString() {
        return "TypeDefinition{" +
                "typeName='" + typeName + '\'' +
                '}';
    }
}
