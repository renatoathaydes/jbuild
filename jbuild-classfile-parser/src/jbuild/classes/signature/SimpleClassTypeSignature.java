package jbuild.classes.signature;

import jbuild.classes.TypeGroup;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
public final class SimpleClassTypeSignature
        implements TypeGroup {
    public final String identifier;
    public final List<TypeArgument> typeArguments;

    // TODO signature param?
    public SimpleClassTypeSignature(String signature, String identifier, List<TypeArgument> typeArguments) {
        this.identifier = identifier;
        this.typeArguments = typeArguments;
    }

    public SimpleClassTypeSignature(String signature, String identifier) {
        this(signature, identifier, List.of());
    }

    @Override
    public Set<String> getAllTypes() {
        return typeArguments.stream()
                .flatMap(a -> a.getAllTypes().stream())
                .collect(Collectors.toSet());
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

    public interface TypeArgument extends TypeGroup {

        enum Star implements TypeArgument {
            INSTANCE;

            @Override
            public Set<String> getAllTypes() {
                return Set.of();
            }
        }

        final class Reference implements TypeArgument {
            private final WildCardIndicator wildCardIndicator;
            public final JavaTypeSignature.ReferenceTypeSignature typeSignature;

            public Reference(JavaTypeSignature.ReferenceTypeSignature typeSignature, WildCardIndicator wildCardIndicator) {
                this.typeSignature = typeSignature;
                this.wildCardIndicator = wildCardIndicator;
            }

            public Reference(JavaTypeSignature.ReferenceTypeSignature typeSignature) {
                this(typeSignature, null);
            }

            public Optional<WildCardIndicator> getWildCardIndicator() {
                return Optional.ofNullable(wildCardIndicator);
            }

            @Override
            public Set<String> getAllTypes() {
                return typeSignature.getAllTypes();
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
