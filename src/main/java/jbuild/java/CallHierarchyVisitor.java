package jbuild.java;

import jbuild.java.code.Code;
import jbuild.java.code.Definition;
import jbuild.util.Describable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class CallHierarchyVisitor {

    private final ClassGraph classGraph;
    private final Set<Pattern> typeExclusions;

    public CallHierarchyVisitor(ClassGraph classGraph,
                                Set<Pattern> typeExclusions) {
        this.classGraph = classGraph;
        this.typeExclusions = typeExclusions;
    }

    public void visitAll(Visitor visitor) {
        for (var entry : classGraph.getTypesByJar().entrySet()) {
            var jar = entry.getKey();
            for (var typeDef : entry.getValue().values()) {
                var chain = new ArrayList<Describable>();
                visit(chain, new ClassGraph.TypeDefinitionLocation(typeDef, jar), visitor);
            }
        }
    }

    public void visit(ClassGraph.TypeDefinitionLocation typeDefinition, Visitor visitor) {
        var chain = new ArrayList<Describable>();
        visit(chain, typeDefinition, visitor);
    }

    private void visit(List<Describable> chain,
                       ClassGraph.TypeDefinitionLocation typeDef,
                       Visitor visitor) {
        if (!visitType(chain, typeDef, visitor)) return;
        var typeDefinition = typeDef.typeDefinition;
        for (var methodHandle : typeDefinition.usedMethodHandles) {
            visitMethod(chain, methodHandle, visitor);
        }
        typeDefinition.methods.forEach((method, codes) -> {
            visitor.visit(chain, method);
            chain.add(method);
            visitCodes(chain, codes, visitor);
            chain.remove(chain.size() - 1);
        });
        for (var field : typeDefinition.fields) {
            visitor.visit(chain, field);
            chain.add(field);
            visitField(chain, null, field.type, field.name, visitor);
            chain.remove(chain.size() - 1);
        }
    }

    private void visitCodes(List<Describable> chain, Set<Code> codes, Visitor visitor) {
        for (var code : codes) {
            code.use(t -> visitType(chain, t.typeName, visitor),
                    f -> visitField(chain, f.typeName, f.type, f.name, visitor),
                    m -> visitMethod(chain, m, visitor));
        }
    }

    private ClassGraph.TypeDefinitionLocation visitType(List<Describable> chain,
                                                        String typeName,
                                                        Visitor visitor) {
        if (typeExclusions.stream().anyMatch(p -> p.matcher(typeName).matches())) return null;
        var typeDef = classGraph.findTypeDefinitionLocation(typeName);
        if (typeDef == null) {
            visitor.onMissingType(chain, typeName);
        } else {
            visitType(chain, typeDef, visitor);
        }
        return typeDef;
    }

    private boolean visitType(List<Describable> chain,
                              ClassGraph.TypeDefinitionLocation typeDef,
                              Visitor visitor) {
        var typeName = typeDef.typeDefinition.typeName;
        if (typeExclusions.stream().anyMatch(p -> p.matcher(typeName).matches())) {
            return false;
        }
        visitor.visit(chain, typeDef);
        chain.add(typeDef);
        typeDef.typeDefinition.type.typesReferredTo().forEach((type) ->
                visitType(chain, type, visitor));
        chain.remove(chain.size() - 1);
        return true;
    }

    private void visitField(List<Describable> chain,
                            // may be null if the type doesn't need to be visited
                            String typeName,
                            String fieldType,
                            String fieldName,
                            Visitor visitor) {
        if (typeName != null) {
            var typeDef = visitType(chain, typeName, visitor);
            if (typeDef != null) {
                chain.add(typeDef);
                var field = typeDef.typeDefinition.fields.stream()
                        .filter(f -> f.name.equals(fieldName))
                        .findFirst();
                if (field.isPresent()) {
                    visitor.visit(chain, field.get());
                } else {
                    visitor.onMissingField(chain, typeDef, fieldName);
                }
                chain.remove(chain.size() - 1);
            }
        }
        visitType(chain, fieldType, visitor);
    }

    private void visitMethod(List<Describable> chain, Code.Method method, Visitor visitor) {
        var methodDef = method.toDefinition();
        chain.add(methodDef);
        visitType(chain, methodDef.getReturnType(), visitor);
        for (var parameterType : methodDef.getParameterTypes()) {
            visitType(chain, parameterType, visitor);
        }
        var typeOwner = visitType(chain, method.typeName, visitor);
        if (typeOwner != null) {
            var codes = typeOwner.typeDefinition.methods.get(methodDef);
            if (codes == null) {
                visitor.onMissingMethod(chain, typeOwner, methodDef);
            } else {
                visitor.visit(chain, methodDef);
                visitCodes(chain, codes, visitor);
            }
        }
        chain.remove(chain.size() - 1);
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
         * Visit a code definition (field access, method call).
         *
         * @param referenceChain current location
         * @param definition     to visit
         */
        void visit(List<Describable> referenceChain, Definition definition);

        void onMissingType(List<Describable> referenceChain, String typeName);

        void onMissingMethod(List<Describable> referenceChain,
                             ClassGraph.TypeDefinitionLocation typeDefinitionLocation,
                             Definition.MethodDefinition methodDefinition);

        void onMissingField(List<Describable> referenceChain,
                            ClassGraph.TypeDefinitionLocation typeDefinitionLocation,
                            String fieldName);
    }

}
