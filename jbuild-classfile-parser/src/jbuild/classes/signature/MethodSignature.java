package jbuild.classes.signature;

import jbuild.classes.TypeGroup;
import jbuild.classes.model.attributes.SignatureAttribute;
import jbuild.classes.signature.JavaTypeSignature.ReferenceTypeSignature.TypeVariableSignature;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A method signature encodes type information about a (possibly generic) method declaration.
 * <p>
 * It describes any type parameters of the method; the (possibly parameterized) types of any formal
 * parameters; the (possibly parameterized) return type, if any; and the types of any exceptions
 * declared in the method's throws clause.
 * <pre>
 * MethodSignature:
 *   [TypeParameters] ( {JavaTypeSignature} ) Result {ThrowsSignature}
 * Result:
 *   JavaTypeSignature
 *   VoidDescriptor
 * ThrowsSignature:
 *   ^ ClassTypeSignature
 *   ^ TypeVariableSignature
 * </pre>
 */
public final class MethodSignature extends SignatureAttribute {

    public final List<TypeParameter> typeParameters;
    public final List<JavaTypeSignature> arguments;
    public final MethodResult result;
    public final List<ThrowsSignature> throwsSignatures;

    public MethodSignature(String signature,
                           List<TypeParameter> typeParameters,
                           List<JavaTypeSignature> arguments,
                           MethodResult result,
                           List<ThrowsSignature> throwsSignatures) {
        super(signature);
        this.typeParameters = typeParameters;
        this.arguments = arguments;
        this.result = result;
        this.throwsSignatures = throwsSignatures;
    }

    public MethodSignature(String signature,
                           List<JavaTypeSignature> arguments,
                           MethodResult result) {
        this(signature, List.of(), arguments, result, List.of());
    }

    @Override
    public Set<String> getAllTypes() {
        var typeParams = typeParameters.stream()
                .flatMap(p -> p.getAllTypes().stream())
                .collect(Collectors.toList());
        var argsTypes = arguments.stream()
                .flatMap(p -> p.getAllTypes().stream())
                .collect(Collectors.toList());
        var resultTypes = result.getAllTypes();
        var throwsTypes = throwsSignatures.stream()
                .flatMap(p -> p.getAllTypes().stream())
                .collect(Collectors.toList());
        var result = new HashSet<String>(
                typeParams.size() + argsTypes.size() +
                        resultTypes.size() + throwsTypes.size());
        result.addAll(typeParams);
        result.addAll(argsTypes);
        result.addAll(resultTypes);
        result.addAll(throwsTypes);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MethodSignature that = (MethodSignature) o;

        if (!typeParameters.equals(that.typeParameters)) return false;
        if (!arguments.equals(that.arguments)) return false;
        if (!result.equals(that.result)) return false;
        return throwsSignatures.equals(that.throwsSignatures);
    }

    @Override
    public int hashCode() {
        int result1 = typeParameters.hashCode();
        result1 = 31 * result1 + arguments.hashCode();
        result1 = 31 * result1 + result.hashCode();
        result1 = 31 * result1 + throwsSignatures.hashCode();
        return result1;
    }

    @Override
    public String toString() {
        return "MethodSignature{" +
                "typeParameters=" + typeParameters +
                ", arguments=" + arguments +
                ", result=" + result +
                ", throwsSignatures=" + throwsSignatures +
                '}';
    }

    public interface MethodResult extends TypeGroup {
        final class MethodReturnType implements MethodResult {
            public final JavaTypeSignature typeSignature;

            public MethodReturnType(JavaTypeSignature typeSignature) {
                this.typeSignature = typeSignature;
            }

            @Override
            public Set<String> getAllTypes() {
                return typeSignature.getAllTypes();
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                MethodReturnType that = (MethodReturnType) o;

                return typeSignature.equals(that.typeSignature);
            }

            @Override
            public int hashCode() {
                return typeSignature.hashCode();
            }

            @Override
            public String toString() {
                return "MethodReturnType{" +
                        "typeSignature=" + typeSignature +
                        '}';
            }
        }

        enum VoidDescriptor implements MethodResult {
            INSTANCE;

            @Override
            public Set<String> getAllTypes() {
                return Set.of();
            }
        }
    }

    public interface ThrowsSignature extends TypeGroup {
        final class Class implements ThrowsSignature {
            public final JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature typeSignature;

            public Class(JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature typeSignature) {
                this.typeSignature = typeSignature;
            }

            @Override
            public Set<String> getAllTypes() {
                return typeSignature.getAllTypes();
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                Class aClass = (Class) o;

                return typeSignature.equals(aClass.typeSignature);
            }

            @Override
            public int hashCode() {
                return typeSignature.hashCode();
            }

            @Override
            public String toString() {
                return "Class{" +
                        "typeSignature=" + typeSignature +
                        '}';
            }
        }

        final class TypeVariable implements ThrowsSignature {
            public final TypeVariableSignature typeSignature;

            public TypeVariable(TypeVariableSignature typeSignature) {
                this.typeSignature = typeSignature;
            }

            @Override
            public Set<String> getAllTypes() {
                return typeSignature.getAllTypes();
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                TypeVariable that = (TypeVariable) o;

                return typeSignature.equals(that.typeSignature);
            }

            @Override
            public int hashCode() {
                return typeSignature.hashCode();
            }

            @Override
            public String toString() {
                return "TypeVariable{" +
                        "typeSignature=" + typeSignature +
                        '}';
            }
        }
    }
}
