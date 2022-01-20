package jbuild.java.code;

import jbuild.java.JavaType;

import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * Definition of a Java type.
 * <p>
 * Includes the type's non-private API, method definitions,
 * fields and method handles used in the logic of this type.
 */
public final class TypeDefinition {

    public final JavaType type;
    public final String typeName;
    public final Set<String> implementedInterfaces;
    public final Set<Definition.FieldDefinition> fields;
    // method handles are "special" in that they don't show up directly in the bytecode where they are used,
    // but are only referenced from the constant table (see the "FunctionalCode" test class' "peek(log::debug)" ref).
    public final Set<Code.Method> usedMethodHandles;
    public final Map<Definition.MethodDefinition, Set<Code>> methods;

    public TypeDefinition(JavaType type,
                          Set<Definition.FieldDefinition> fields,
                          Set<Code.Method> usedMethodHandles,
                          Map<Definition.MethodDefinition, Set<Code>> methods) {
        this.type = type;
        this.fields = fields;
        this.usedMethodHandles = usedMethodHandles;
        this.methods = methods;

        this.typeName = type.typeId.name;
        this.implementedInterfaces = type.interfaces.stream()
                .map(bound -> bound.name)
                .collect(toSet());
    }

    @Override
    public String toString() {
        return "TypeDefinition{" +
                "type='" + type + '\'' +
                ", fields=" + fields +
                ", methodHandles=" + usedMethodHandles +
                ", methods=" + methods +
                '}';
    }
}
