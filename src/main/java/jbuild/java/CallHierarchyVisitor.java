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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static jbuild.java.CallHierarchyVisitor.TypeInfo.Kind.JAVA_STDLIB;
import static jbuild.java.CallHierarchyVisitor.TypeInfo.Kind.LIBRARY;
import static jbuild.java.CallHierarchyVisitor.TypeInfo.Kind.PRIMITIVE;
import static jbuild.util.AsyncUtils.returning;
import static jbuild.util.JavaTypeUtils.mayBeJavaStdLibType;
import static jbuild.util.JavaTypeUtils.typeNameToClassName;

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

    public CallHierarchyVisitor(ClassGraph classGraph) {
        this(classGraph, Set.of(), Set.of());
    }

    /**
     * Visit the types in the given entry jars, taking into consideration the
     * type inclusions and exclusions for this visitor.
     *
     * @param entryJars whose types to visit
     * @param visitor   to use
     */
    public void visit(Set<File> entryJars, Visitor visitor) {
        Set<String> visitedTypes = new HashSet<>();
        var typeLocations = new ArrayList<ClassGraph.TypeDefinitionLocation>();

        for (var jar : entryJars) {
            visitor.startJar(jar);
            var types = classGraph.getTypesByJar().get(jar);

            // visit type locations first
            for (var typeDef : types.values()) {
                var typeLocation = new ClassGraph.TypeDefinitionLocation(typeDef, jar);
                if (typeFilter.test(typeLocation.className)) {
                    visitType(new ArrayList<>(), typeLocation, visitedTypes, visitor);
                    typeLocations.add(typeLocation);
                }
            }
        }

        // visit types again including members and referenced types
        var chain = new ArrayList<Describable>();
        for (var typeLocation : typeLocations) {
            visitAll(chain, typeLocation, visitedTypes, visitor);
        }
    }

    public boolean visit(Code code, Visitor visitor) {
        var typeLocation = classGraph.findTypeDefinitionLocation(code.typeName);
        if (typeLocation != null) {
            visitCodes(new ArrayList<>(), Set.of(code), new HashSet<>(), new HashSet<>(), visitor);
            return true;
        }
        return false;
    }

    private void visitAll(List<Describable> chain,
                          ClassGraph.TypeDefinitionLocation typeLocation,
                          Set<String> visitedTypes,
                          Visitor visitor) {
        visitType(chain, typeLocation, visitedTypes, visitor);
        var ok = visitTypesReferredTo(chain, typeLocation, visitedTypes, visitor);
        if (!ok) {
            // abort visiting, types are missing so there's no point
            return;
        }
        var typeDefinition = typeLocation.typeDefinition;
        chain.add(typeLocation);

        for (var field : typeDefinition.fields) {
            ok &= visitFieldDefinition(chain, visitedTypes, field, visitor);
        }
        if (!ok) {
            // abort visiting, types are missing so there's no point
            return;
        }
        for (var entry : typeDefinition.methods.entrySet()) {
            Definition.MethodDefinition method = entry.getKey();
            Set<Code> codes = entry.getValue();
            ok &= visitMethodDefinition(chain, visitedTypes, method, visitor);
            visitor.visit(chain, method);
            chain.add(method);
            visitCodes(chain, codes, visitedTypes, new HashSet<>(), visitor);
            chain.remove(chain.size() - 1);
        }
        if (!ok) {
            // abort visiting, types are missing so there's no point
            return;
        }
        visitCodes(chain, typeDefinition.usedMethodHandles, visitedTypes, new HashSet<>(), visitor);

        chain.remove(chain.size() - 1);
    }

    private void visitType(List<Describable> chain,
                           ClassGraph.TypeDefinitionLocation typeLocation,
                           Set<String> visitedTypes,
                           Visitor visitor) {
        if (!visitedTypes.add(typeLocation.typeDefinition.typeName)
                || !typeFilter.test(typeLocation.className)) {
            return;
        }
        visitor.visit(chain, typeLocation);
    }

    private boolean visitTypesReferredTo(List<Describable> chain,
                                         ClassGraph.TypeDefinitionLocation typeLocation,
                                         Set<String> visitedTypes,
                                         Visitor visitor) {
        chain.add(typeLocation);
        var allTypesOk = new AtomicBoolean(true);
        typeLocation.typeDefinition.type.typesReferredTo().forEach((type) -> {
            var typeInfo = visitTypeName(chain, type, visitedTypes, visitor);
            if (typeInfo == null) {
                allTypesOk.set(false);
            }
        });
        chain.remove(chain.size() - 1);
        return allTypesOk.get();
    }

    private TypeInfo visitTypeName(List<Describable> chain,
                                   String typeName,
                                   Set<String> visitedTypes,
                                   Visitor visitor) {
        var cleanTypeName = JavaTypeUtils.cleanArrayTypeName(typeName);
        var isPrimitive = JavaTypeUtils.isPrimitiveJavaType(cleanTypeName);
        if (isPrimitive || mayBeJavaStdLibType(cleanTypeName)) {
            return new TypeInfo(null, isPrimitive ? PRIMITIVE : JAVA_STDLIB);
        }
        var typeLocation = classGraph.findTypeDefinitionLocation(cleanTypeName);
        if (visitedTypes.contains(cleanTypeName)) {
            return typeLocation == null ? null : new TypeInfo(typeLocation, LIBRARY);
        }
        if (typeLocation == null) {
            if (typeFilter.test(typeNameToClassName(cleanTypeName))) {
                visitor.onMissingType(chain, cleanTypeName);
            }
            return null;
        }
        visitType(chain, typeLocation, visitedTypes, visitor);
        return new TypeInfo(typeLocation, LIBRARY);
    }

    private boolean visitFieldDefinition(List<Describable> chain,
                                         Set<String> visitedTypes,
                                         Definition.FieldDefinition field,
                                         Visitor visitor) {
        visitor.visit(chain, field);
        chain.add(field);
        var ok = visitTypeName(chain, field.type, visitedTypes, visitor) != null;
        chain.remove(chain.size() - 1);
        return ok;
    }

    private void visitCodes(List<Describable> chain,
                            Set<? extends Code> codes,
                            Set<String> visitedTypes,
                            Set<Code.Method> visitedMethods,
                            Visitor visitor) {
        for (var code : codes) {
            var ok = code.match(t -> visitTypeName(chain, t.typeName, visitedTypes, visitor) != null,
                    returning(true, f -> visitFieldUsage(chain, f, visitedTypes, visitor)),
                    returning(true, m -> visitMethodUsage(chain, visitedTypes, visitedMethods, m, visitor)));
            if (!ok) {
                // give up, the method is already broken
                return;
            }
        }
    }

    private void visitFieldUsage(List<Describable> chain,
                                 Code.Field field,
                                 Set<String> visitedTypes,
                                 Visitor visitor) {

        var typeInfo = visitTypeName(chain, field.typeName, visitedTypes, visitor);
        if (typeInfo != null) {
            chain.add(field);
            switch (typeInfo.kind) {
                case PRIMITIVE:
                    throw new IllegalStateException("Primitive type cannot have field: " + field);
                case JAVA_STDLIB:
                    visitJavaField(chain, field.typeName, field, visitor);
                    break;
                case LIBRARY:
                    visitFieldUsage(chain, typeInfo.typeDefinitionLocation, field, visitor);
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

    private void visitFieldUsage(List<Describable> chain,
                                 ClassGraph.TypeDefinitionLocation typeDef,
                                 Code.Field field,
                                 Visitor visitor) {
        if (classGraph.exists(field)) {
            visitor.visit(chain, field);
        } else {
            visitor.onMissingField(chain, typeDef, field);
        }
    }

    private boolean visitMethodDefinition(List<Describable> chain,
                                          Set<String> visitedTypes,
                                          Definition.MethodDefinition methodDef,
                                          Visitor visitor) {
        chain.add(methodDef);
        var ok = visitTypeName(chain, methodDef.getReturnType(), visitedTypes, visitor) != null;
        for (var parameterType : methodDef.getParameterTypes()) {
            ok &= visitTypeName(chain, parameterType, visitedTypes, visitor) != null;
        }
        chain.remove(chain.size() - 1);
        return ok;
    }

    private void visitMethodUsage(List<Describable> chain,
                                  Set<String> visitedTypes,
                                  Set<Code.Method> visitedMethods,
                                  Code.Method method,
                                  Visitor visitor) {
        if (!visitedMethods.add(method)) return;
        chain.add(method);
        var typeOwner = visitTypeName(chain, method.typeName, visitedTypes, visitor);
        chain.remove(chain.size() - 1);
        var ok = typeOwner != null &&
                visitMethodDefinition(chain, visitedTypes, method.toDefinition(), visitor);
        if (ok && typeOwner.kind == LIBRARY) {
            var codes = classGraph.findImplementation(method);
            if (codes == null) {
                // do not report error if parent types are excluded as we can't know for sure if the method doesn't exist
                var cannotDetermineForSure = isAnyParentTypeExcluded(typeOwner);
                if (!cannotDetermineForSure) {
                    visitor.onMissingMethod(chain, typeOwner.typeDefinitionLocation, method);
                }
            } else {
                visitor.visit(chain, method);
                if (!codes.isEmpty()) {
                    chain.add(method);
                    visitCodes(chain, codes, visitedTypes, visitedMethods, visitor);
                    chain.remove(chain.size() - 1);
                }
            }
        }
    }

    private boolean isAnyParentTypeExcluded(TypeInfo typeOwner) {
        return typeOwner.typeDefinitionLocation.typeDefinition.type.typesReferredTo()
                .map(JavaTypeUtils::typeNameToClassName)
                .anyMatch(className -> !typeFilter.test(className));
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
         * Start visiting jar.
         *
         * @param jar file to be visited
         */
        void startJar(File jar);

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
