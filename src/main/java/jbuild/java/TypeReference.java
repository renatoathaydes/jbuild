package jbuild.java;

import jbuild.java.code.Definition;
import jbuild.java.code.TypeDefinition;
import jbuild.util.JavaTypeUtils;
import jbuild.util.NoOp;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.joining;
import static jbuild.util.JavaTypeUtils.cleanArrayTypeName;
import static jbuild.util.JavaTypeUtils.isReferenceType;
import static jbuild.util.JavaTypeUtils.mayBeJavaStdLibType;
import static jbuild.util.JavaTypeUtils.typeNameToClassName;

public final class TypeReference {

    public final File jar;
    public final String typeFrom;
    public final Set<String> typesTo;

    public TypeReference(File jar, String typeFrom, Set<String> typesTo) {
        this.jar = jar;
        this.typeFrom = typeFrom;
        this.typesTo = typesTo;
    }

    @Override
    public String toString() {
        return jar.getName() + '!' + typeNameToClassName(typeFrom) + " -> " + typesTo.stream()
                .map(JavaTypeUtils::typeNameToClassName)
                .sorted()
                .collect(joining(", "));
    }

    static final class Collector {

        private final File jar;
        private final List<TypeReference> typeReferences;
        private final Set<Pattern> typeExclusions;

        public Collector(File jar,
                         List<TypeReference> typeReferences,
                         Set<Pattern> typeExclusions) {
            this.jar = jar;
            this.typeReferences = typeReferences;
            this.typeExclusions = typeExclusions;
        }

        void collect(TypeDefinition typeDef) {
            var result = new HashSet<String>();
            addReferenceTypesTo(result, () -> typeDef.type.typesReferredTo().iterator());
            for (var field : typeDef.fields) {
                addReferenceTypeTo(result, field.type);
            }
            for (var methodDef : typeDef.methods.keySet()) {
                addTypesReferredToFromMethodInto(result, methodDef);
            }
            for (var method : typeDef.usedMethodHandles) {
                addReferenceTypeTo(result, method.typeName);
                var def = method.toDefinition();
                addTypesReferredToFromMethodInto(result, def);
            }
            for (var codes : typeDef.methods.values()) {
                for (var code : codes) {
                    addReferenceTypeTo(result, code.typeName);
                    code.match(
                            NoOp.fun(),
                            f -> addReferenceTypeTo(result, f.type),
                            m -> addTypesReferredToFromMethodInto(result, m.toDefinition()));
                }
            }
            if (!result.isEmpty()) {
                typeReferences.add(new TypeReference(jar, typeDef.typeName, result));
            }
        }

        private Void addTypesReferredToFromMethodInto(Set<String> result,
                                                      Definition.MethodDefinition methodDef) {
            addReferenceTypesTo(result, methodDef.getParameterTypes());
            addReferenceTypeTo(result, methodDef.getReturnType());
            return null;
        }

        private void addReferenceTypesTo(Set<String> result, Iterable<String> types) {
            for (var type : types) {
                addReferenceTypeTo(result, type);
            }
        }

        private Void addReferenceTypeTo(Set<String> result, String type) {
            var cleanType = cleanArrayTypeName(type);
            if (isReferenceType(cleanType)
                    && !mayBeJavaStdLibType(cleanType)
                    && typeExclusions.stream().noneMatch(p -> p.matcher(cleanType).matches())) {
                result.add(cleanType);
            }
            return null;
        }

    }
}
