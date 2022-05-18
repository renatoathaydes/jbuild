package jbuild.java;

import jbuild.java.code.Definition;
import jbuild.java.code.TypeDefinition;
import jbuild.util.Describable;
import jbuild.util.NoOp;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static jbuild.util.JavaTypeUtils.cleanArrayTypeName;
import static jbuild.util.JavaTypeUtils.isReferenceType;
import static jbuild.util.JavaTypeUtils.mayBeJavaStdLibType;
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

    static final class Collector {

        private final Jar.ParsedJar jar;
        private final List<TypeReference> typeReferences;
        private final Set<Pattern> typeExclusions;

        public Collector(Jar.ParsedJar jar,
                         List<TypeReference> requiredTypes,
                         Set<Pattern> typeExclusions) {
            this.jar = jar;
            this.typeReferences = requiredTypes;
            this.typeExclusions = typeExclusions;
        }

        void collect() {
            for (var typeDef : jar.typeByName.values()) {
                collect(typeDef);
            }
        }

        void collect(TypeDefinition typeDef) {
            addReferenceTypes(null, typeDef, () -> typeDef.type.typesReferredTo().iterator());
            for (var field : typeDef.fields) {
                addReferenceType(typeDef, field, field.type);
            }
            for (var methodDef : typeDef.methods.keySet()) {
                addTypesReferredToFromMethod(typeDef, methodDef);
            }
            for (var method : typeDef.usedMethodHandles) {
                addReferenceType(typeDef, method, method.typeName);
                addTypesReferredToFromMethod(typeDef, method.toDefinition());
            }
            for (var entry : typeDef.methods.entrySet()) {
                var method = entry.getKey();
                var codes = entry.getValue();
                for (var code : codes) {
                    addReferenceType(typeDef, method, code.typeName);
                    code.use(
                            NoOp.ignore(),
                            f -> addReferenceType(typeDef, method, f.type),
                            m -> {
                                var methodCall = m.toDefinition();
                                addReferenceTypes(typeDef, method, methodCall.getParameterTypes());
                                addReferenceType(typeDef, method, methodCall.getReturnType());
                            });
                }
            }
        }

        private void addTypesReferredToFromMethod(TypeDefinition typeDef,
                                                  Definition.MethodDefinition methodDef) {
            addReferenceTypes(typeDef, methodDef, methodDef.getParameterTypes());
            addReferenceType(typeDef, methodDef, methodDef.getReturnType());
        }

        private void addReferenceTypes(TypeDefinition typeDef, Describable from, Iterable<String> types) {
            for (var type : types) {
                addReferenceType(typeDef, from, type);
            }
        }

        private void addReferenceType(TypeDefinition typeDef, Describable from, String typeTo) {
            var cleanType = cleanArrayTypeName(typeTo);
            if (!jar.typeByName.containsKey(cleanType) &&
                    isReferenceType(cleanType)
                    && !mayBeJavaStdLibType(cleanType)
                    && typeExclusions.stream().noneMatch(p -> p.matcher(cleanType).matches())) {
                var fromRef = typeDef == null ? from : new TypeMember(typeDef, from);
                typeReferences.add(new TypeReference(jar.file, fromRef, cleanType));
            }
        }

    }

    private static final class TypeMember implements Describable {
        private final TypeDefinition typeDefinition;
        private final Describable member;

        public TypeMember(TypeDefinition typeDefinition, Describable member) {
            this.typeDefinition = typeDefinition;
            this.member = member;
        }

        @Override
        public void describe(StringBuilder builder, boolean verbose) {
            typeDefinition.describe(builder, verbose);
            builder.append("::");
            member.describe(builder, verbose);
        }
    }
}
