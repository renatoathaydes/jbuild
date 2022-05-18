package jbuild.java;

import jbuild.java.code.Code;
import jbuild.java.code.Definition;
import jbuild.util.Describable;
import jbuild.util.JavaTypeUtils;
import jbuild.util.NoOp;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static jbuild.java.CallHierarchyVisitor.TypeInfo.Kind.JAVA_STDLIB;
import static jbuild.java.CallHierarchyVisitor.TypeInfo.Kind.LIBRARY;
import static jbuild.java.CallHierarchyVisitor.TypeInfo.Kind.PRIMITIVE;
import static jbuild.util.JavaTypeUtils.mayBeJavaStdLibType;

public final class CallHierarchyVisitor {

    private final ClassGraph classGraph;
    private final Predicate<String> typeFilter;

    public CallHierarchyVisitor(ClassGraph classGraph,
                                Set<Pattern> typeExclusions,
                                Set<Pattern> typeInclusions) {
        this.classGraph = classGraph;

        if (typeExclusions.isEmpty() && typeInclusions.isEmpty()) {
            typeFilter = NoOp.all();
        } else if (!typeInclusions.isEmpty()) {
            typeFilter = new TypeInclusions(typeInclusions);
        } else {
            typeFilter = new TypeExclusions(typeExclusions);
        }
    }

    public CallHierarchyVisitor(ClassGraph classGraph,
                                Set<Pattern> typeExclusions) {
        this(classGraph, typeExclusions, Set.of());
    }

    public void visit(Set<File> entryJars, Visitor visitor) {
        Set<String> visitedTypes = new HashSet<>();
        for (var jar : entryJars) {
            for (var typeDef : classGraph.getTypesByJar().get(jar).values()) {
                var chain = new ArrayList<Describable>();
                visit(chain, new ClassGraph.TypeDefinitionLocation(typeDef, jar), visitedTypes, visitor);
            }
        }
    }

    private void visit(List<Describable> chain,
                       ClassGraph.TypeDefinitionLocation typeDef,
                       Set<String> visitedTypes,
                       Visitor visitor) {
        if (!visitType(chain, typeDef, visitedTypes, visitor)) return;
        var typeDefinition = typeDef.typeDefinition;
        chain.add(typeDef);

        typeDefinition.methods.forEach((method, codes) -> {
            visitMethodDefinition(chain, visitedTypes, method, visitor);
            visitor.visit(chain, method);
            chain.add(method);
            visitCodes(chain, codes, visitedTypes, visitor);
            chain.remove(chain.size() - 1);
        });

        for (var field : typeDefinition.fields) {
            visitFieldDefinition(chain, visitedTypes, field, visitor);
        }

        visitCodes(chain, typeDefinition.usedMethodHandles, visitedTypes, visitor);

        chain.remove(chain.size() - 1);
    }

    private void visitFieldDefinition(List<Describable> chain,
                                      Set<String> visitedTypes,
                                      Definition.FieldDefinition field,
                                      Visitor visitor) {
        visitor.visit(chain, field);
        chain.add(field);
        visitType(chain, field.type, visitedTypes, visitor);
        chain.remove(chain.size() - 1);
    }

    private void visitCodes(List<Describable> chain, Set<? extends Code> codes,
                            Set<String> visitedTypes, Visitor visitor) {
        for (var code : codes) {
            code.use(t -> visitType(chain, t.typeName, visitedTypes, visitor),
                    f -> visitFieldUsage(chain, f, visitedTypes, visitor),
                    m -> visitMethodUsage(chain, visitedTypes, m, visitor));
        }
    }

    private TypeInfo visitType(List<Describable> chain,
                               String typeName,
                               Set<String> visitedTypes,
                               Visitor visitor) {
        var cleanTypeName = JavaTypeUtils.cleanArrayTypeName(typeName);
        var isPrimitive = JavaTypeUtils.isPrimitiveJavaType(cleanTypeName);
        if (isPrimitive || mayBeJavaStdLibType(cleanTypeName)) {
            return new TypeInfo(null, isPrimitive ? PRIMITIVE : JAVA_STDLIB);
        }
        var typeDef = classGraph.findTypeDefinitionLocation(cleanTypeName);
        if (visitedTypes.contains(cleanTypeName)) {
            return (typeDef == null) ? null : new TypeInfo(typeDef, LIBRARY);
        }
        if (typeDef == null) {
            if (typeFilter.test(cleanTypeName)) visitor.onMissingType(chain, cleanTypeName);
            return null;
        }
        visitType(chain, typeDef, visitedTypes, visitor);
        return new TypeInfo(typeDef, LIBRARY);
    }

    private boolean visitType(List<Describable> chain,
                              ClassGraph.TypeDefinitionLocation typeDef,
                              Set<String> visitedTypes,
                              Visitor visitor) {
        var typeName = typeDef.typeDefinition.typeName;
        if (visitedTypes.contains(typeName) || !typeFilter.test(typeName)) {
            return false;
        }
        visitedTypes.add(typeName);
        visitor.visit(chain, typeDef);
        chain.add(typeDef);
        typeDef.typeDefinition.type.typesReferredTo().forEach((type) ->
                visitType(chain, type, visitedTypes, visitor));
        chain.remove(chain.size() - 1);
        return true;
    }

    private void visitFieldUsage(List<Describable> chain,
                                 Code.Field field,
                                 Set<String> visitedTypes,
                                 Visitor visitor) {

        var typeInfo = visitType(chain, field.typeName, visitedTypes, visitor);
        if (typeInfo != null) {
            chain.add(field);
            switch (typeInfo.kind) {
                case PRIMITIVE:
                    throw new IllegalStateException("Primitive type cannot have field: " + field);
                case JAVA_STDLIB:
                    visitJavaField(chain, field.typeName, field, visitor);
                    break;
                case LIBRARY:
                    visitTypeReferenceField(chain, typeInfo.typeDefinitionLocation, field, visitor);
                    break;
            }
            chain.remove(chain.size() - 1);
        }
    }

    private void visitJavaField(List<Describable> chain,
                                String typeName,
                                Code.Field field,
                                Visitor visitor) {
        if (classGraph.existsJava(typeName, field.toDefinition())) {
            visitor.visit(chain, field);
        } else {
            visitor.onMissingField(chain, typeName, field);
        }
    }

    private void visitTypeReferenceField(List<Describable> chain,
                                         ClassGraph.TypeDefinitionLocation typeDef,
                                         Code.Field field,
                                         Visitor visitor) {
        if (classGraph.exists(field)) {
            visitor.visit(chain, field);
        } else {
            visitor.onMissingField(chain, typeDef, field);
        }
    }

    private void visitMethodDefinition(List<Describable> chain,
                                       Set<String> visitedTypes,
                                       Definition.MethodDefinition methodDef,
                                       Visitor visitor) {
        chain.add(methodDef);
        visitType(chain, methodDef.getReturnType(), visitedTypes, visitor);
        for (var parameterType : methodDef.getParameterTypes()) {
            visitType(chain, parameterType, visitedTypes, visitor);
        }
        chain.remove(chain.size() - 1);
    }


    private void visitMethodUsage(List<Describable> chain,
                                  Set<String> visitedTypes,
                                  Code.Method method,
                                  Visitor visitor) {
        chain.add(method);
        var typeOwner = visitType(chain, method.typeName, visitedTypes, visitor);
        chain.remove(chain.size() - 1);
        if (typeOwner != null && typeOwner.kind == LIBRARY) {
            var codes = classGraph.findImplementation(method);
            if (codes == null) {
                visitor.onMissingMethod(chain, typeOwner.typeDefinitionLocation, method);
            } else {
                visitor.visit(chain, method);
                if (!codes.isEmpty()) {
                    chain.add(method);
                    visitCodes(chain, codes, visitedTypes, visitor);
                    chain.remove(chain.size() - 1);
                }
            }
        }
    }

    private static final class TypeInclusions implements Predicate<String> {
        final Set<Pattern> patterns;

        public TypeInclusions(Set<Pattern> patterns) {
            this.patterns = patterns;
        }

        @Override
        public boolean test(String typeName) {
            for (var include : patterns)
                if (include.matcher(typeName).matches())
                    return true;
            return false;
        }
    }

    private static final class TypeExclusions implements Predicate<String> {
        final Set<Pattern> patterns;

        public TypeExclusions(Set<Pattern> patterns) {
            this.patterns = patterns;
        }

        @Override
        public boolean test(String typeName) {
            for (var exclude : patterns)
                if (exclude.matcher(typeName).matches())
                    return false;
            return true;
        }
    }

    static final class TypeInfo {
        enum Kind {
            PRIMITIVE, JAVA_STDLIB, LIBRARY,
        }

        final ClassGraph.TypeDefinitionLocation typeDefinitionLocation;
        final Kind kind;

        public TypeInfo(ClassGraph.TypeDefinitionLocation typeDefinitionLocation, Kind kind) {
            this.typeDefinitionLocation = typeDefinitionLocation;
            this.kind = kind;
        }
    }

    public interface Visitor {

        /**
         * Visit a type definition.
         *
         * @param referenceChain         current location
         * @param typeDefinitionLocation to visit
         */
        void visit(List<Describable> referenceChain,
                   ClassGraph.TypeDefinitionLocation typeDefinitionLocation);

        /**
         * Visit a definition (field or method defined on a type).
         *
         * @param referenceChain current location
         * @param definition     to visit
         */
        void visit(List<Describable> referenceChain, Definition definition);

        /**
         * Visit a code usage (method call, field access or type )
         *
         * @param referenceChain current location
         * @param code           to visit
         */
        void visit(List<Describable> referenceChain, Code code);

        void onMissingType(List<Describable> referenceChain, String typeName);

        void onMissingMethod(List<Describable> referenceChain,
                             ClassGraph.TypeDefinitionLocation typeDefinitionLocation,
                             Code.Method method);

        void onMissingField(List<Describable> referenceChain,
                            ClassGraph.TypeDefinitionLocation typeDefinitionLocation,
                            Code.Field field);

        /**
         * Visit a missing field reference when a {@link jbuild.java.ClassGraph.TypeDefinitionLocation}
         * cannot be provided as the type is a Java type.
         *
         * @param referenceChain current location
         * @param javaTypeName   java type name
         * @param field          the missing field
         */
        void onMissingField(List<Describable> referenceChain,
                            String javaTypeName,
                            Code.Field field);
    }

}
