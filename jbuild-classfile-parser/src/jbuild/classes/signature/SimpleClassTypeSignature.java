package jbuild.classes.signature;

import java.util.List;
import java.util.Optional;

/**
 * A component of a {@link jbuild.classes.signature.JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature}.
 *
 * <pre>
 * SimpleClassTypeSignature:
 *   Identifier [TypeArguments]
 * TypeArguments:
 *   < TypeArgument {TypeArgument} >
 * TypeArgument:
 *   [WildcardIndicator] ReferenceTypeSignature
 *   *
 * WildcardIndicator:
 *   +
 *   -
 * </pre>
 */
public final class SimpleClassTypeSignature {
    public final String identifier;
    public final List<TypeArgument> typeArguments;

    public SimpleClassTypeSignature(String identifier, List<TypeArgument> typeArguments) {
        this.identifier = identifier;
        this.typeArguments = typeArguments;
    }

    public SimpleClassTypeSignature(String identifier) {
        this(identifier, List.of());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SimpleClassTypeSignature that = (SimpleClassTypeSignature) o;

        if (!identifier.equals(that.identifier)) return false;
        return typeArguments.equals(that.typeArguments);
    }

    @Override
    public int hashCode() {
        int result = identifier.hashCode();
        result = 31 * result + typeArguments.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "SimpleClassTypeSignature{" +
                "identifier='" + identifier + '\'' +
                ", typeArguments=" + typeArguments +
                '}';
    }

    public enum WildCardIndicator {PLUS, MINUS}

    public interface TypeArgument {

        enum Star implements TypeArgument {INSTANCE}

        final class Reference implements TypeArgument {
            private final WildCardIndicator wildCardIndicator;
            public final JavaTypeSignature.ReferenceTypeSignature typeSignature;

            public Reference(JavaTypeSignature.ReferenceTypeSignature typeSignature, WildCardIndicator wildCardIndicator) {
                this.wildCardIndicator = wildCardIndicator;
                this.typeSignature = typeSignature;
            }

            public Reference(JavaTypeSignature.ReferenceTypeSignature typeSignature) {
                this(typeSignature, null);
            }

            public Optional<WildCardIndicator> getWildCardIndicator() {
                return Optional.ofNullable(wildCardIndicator);
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                Reference reference = (Reference) o;

                if (wildCardIndicator != reference.wildCardIndicator) return false;
                return typeSignature.equals(reference.typeSignature);
            }

            @Override
            public int hashCode() {
                int result = wildCardIndicator != null ? wildCardIndicator.hashCode() : 0;
                result = 31 * result + typeSignature.hashCode();
                return result;
            }

            @Override
            public String toString() {
                return "Reference{" +
                        "wildCardIndicator=" + wildCardIndicator +
                        ", typeSignature=" + typeSignature +
                        '}';
            }
        }
    }
}
