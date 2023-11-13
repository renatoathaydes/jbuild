package jbuild.classes.signature;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * <pre>
 * TypeParameter:
 *   Identifier ClassBound {InterfaceBound}
 * ClassBound:
 *   : [ReferenceTypeSignature]
 * InterfaceBound:
 *   : ReferenceTypeSignature
 * </pre>
 */
public final class TypeParameter {
    public final String identifier;
    private final JavaTypeSignature.ReferenceTypeSignature classBoundTypeSignature;
    public final List<JavaTypeSignature.ReferenceTypeSignature> interfaceBoundTypeSignatures;

    public TypeParameter(String identifier,
                         JavaTypeSignature.ReferenceTypeSignature classBoundTypeSignature,
                         List<JavaTypeSignature.ReferenceTypeSignature> interfaceBoundTypeSignatures) {
        this.identifier = identifier;
        this.classBoundTypeSignature = classBoundTypeSignature;
        this.interfaceBoundTypeSignatures = interfaceBoundTypeSignatures;
    }

    public TypeParameter(String identifier) {
        this(identifier, null, List.of());
    }

    public TypeParameter(String identifier, JavaTypeSignature.ReferenceTypeSignature classBoundTypeSignature) {
        this(identifier, classBoundTypeSignature, List.of());
    }

    public Optional<JavaTypeSignature.ReferenceTypeSignature> getClassBoundTypeSignature() {
        return Optional.ofNullable(classBoundTypeSignature);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypeParameter that = (TypeParameter) o;

        if (!identifier.equals(that.identifier)) return false;
        if (!Objects.equals(classBoundTypeSignature, that.classBoundTypeSignature))
            return false;
        return interfaceBoundTypeSignatures.equals(that.interfaceBoundTypeSignatures);
    }

    @Override
    public int hashCode() {
        int result = identifier.hashCode();
        result = 31 * result + (classBoundTypeSignature != null ? classBoundTypeSignature.hashCode() : 0);
        result = 31 * result + interfaceBoundTypeSignatures.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "TypeParameter{" +
                "identifier='" + identifier + '\'' +
                ", classBoundTypeSignature=" + classBoundTypeSignature +
                ", interfaceBoundTypeSignatures=" + interfaceBoundTypeSignatures +
                '}';
    }
}
