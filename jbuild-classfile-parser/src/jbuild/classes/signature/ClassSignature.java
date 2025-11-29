package jbuild.classes.signature;

import jbuild.classes.model.attributes.SignatureAttribute;
import jbuild.classes.signature.JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A class signature encodes type information about a (possibly generic) class or interface declaration.
 * <p>
 * It describes any type parameters of the class or interface, and lists its (possibly parameterized) direct
 * superclass and direct superinterfaces, if any.
 * <p>
 * A type parameter is described by its name, followed by any class bound and interface bounds.
 * <pre>
 * ClassSignature:
 *   [TypeParameters] SuperclassSignature {SuperinterfaceSignature}
 * TypeParameters:
 *   < TypeParameter {TypeParameter} >
 *
 * SuperclassSignature:
 *   ClassTypeSignature
 * SuperinterfaceSignature:
 *   ClassTypeSignature
 * </pre>
 */
public final class ClassSignature extends SignatureAttribute {
    public final List<TypeParameter> typeParameters;
    public final ClassTypeSignature superclassSignature;
    public final List<ClassTypeSignature> superInterfaceSignatures;

    public ClassSignature(String signature,
                          List<TypeParameter> typeParameters,
                          ClassTypeSignature superclassSignature,
                          List<ClassTypeSignature> superInterfaceSignatures) {
        super(signature);
        this.typeParameters = typeParameters;
        this.superclassSignature = superclassSignature;
        this.superInterfaceSignatures = superInterfaceSignatures;
    }

    public ClassSignature(String signature,
                          List<TypeParameter> typeParameters,
                          ClassTypeSignature superclassSignature) {
        this(signature, typeParameters, superclassSignature, List.of());
    }

    @Override
    public Set<String> getAllTypes() {
        return Stream.concat(
                        Stream.concat(typeParameters.stream()
                                        .flatMap(p -> p.interfaceBoundTypeSignatures.stream()
                                                .flatMap(s -> s.getAllTypes().stream())),
                                superclassSignature.getAllTypes().stream()),
                        superInterfaceSignatures.stream()
                                .flatMap(s -> s.getAllTypes().stream()))
                .collect(Collectors.toSet());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ClassSignature that = (ClassSignature) o;

        if (!typeParameters.equals(that.typeParameters)) return false;
        if (!superclassSignature.equals(that.superclassSignature)) return false;
        return superInterfaceSignatures.equals(that.superInterfaceSignatures);
    }

    @Override
    public int hashCode() {
        int result = typeParameters.hashCode();
        result = 31 * result + superclassSignature.hashCode();
        result = 31 * result + superInterfaceSignatures.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ClassSignature{" +
                "typeParameters=" + typeParameters +
                ", superclassSignature=" + superclassSignature +
                ", superInterfaceSignatures=" + superInterfaceSignatures +
                '}';
    }
}
