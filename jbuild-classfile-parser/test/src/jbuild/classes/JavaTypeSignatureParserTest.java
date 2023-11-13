package jbuild.classes;

import jbuild.classes.signature.ClassSignature;
import jbuild.classes.signature.JavaTypeSignature;
import jbuild.classes.signature.SimpleClassTypeSignature;
import jbuild.classes.signature.TypeParameter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaTypeSignatureParserTest {
    private final JavaTypeSignatureParser parser = new JavaTypeSignatureParser();

    @Test
    void canParseBaseTypeSignature() {
        for (var value : JavaTypeSignature.BaseType.values()) {
            assertThat(parser.parseTypeSignature(value.name())).isEqualTo(value);
        }
    }

    @Test
    void canParseArrayTypeSignature() {
        assertThat(parser.parseTypeSignature("[Ljava/lang/Object;"))
                .isEqualTo(new JavaTypeSignature.ReferenceTypeSignature.ArrayTypeSignature((short) 1,
                        new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("java.lang",
                                new SimpleClassTypeSignature("Object"))));
    }

    @Test
    void canParseArrayTypeSignaturePrimitive() {
        assertThat(parser.parseTypeSignature("[B"))
                .isEqualTo(new JavaTypeSignature.ReferenceTypeSignature.ArrayTypeSignature((short) 1,
                        JavaTypeSignature.BaseType.B));
        assertThat(parser.parseTypeSignature("[[Z"))
                .isEqualTo(new JavaTypeSignature.ReferenceTypeSignature.ArrayTypeSignature((short) 2,
                        JavaTypeSignature.BaseType.Z));
        assertThat(parser.parseTypeSignature("[[[D"))
                .isEqualTo(new JavaTypeSignature.ReferenceTypeSignature.ArrayTypeSignature((short) 3,
                        JavaTypeSignature.BaseType.D));
    }

    @Test
    void canParseSimpleClassTypeSignature() {
        assertThat(parser.parseTypeSignature("Ljava/lang/Object;"))
                .isEqualTo(new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("java.lang",
                        new SimpleClassTypeSignature("Object")));
    }

    @Test
    void canParseClassTypeSignatureWithSuffix() {
        assertThat(parser.parseTypeSignature("Lorg/T1.T2;"))
                .isEqualTo(new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("org",
                        new SimpleClassTypeSignature("T1"), List.of(new SimpleClassTypeSignature("T2"))));

        assertThat(parser.parseTypeSignature("Lorg/T1.T2.T3.T4;"))
                .isEqualTo(new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("org",
                        new SimpleClassTypeSignature("T1"),
                        List.of(new SimpleClassTypeSignature("T2"), new SimpleClassTypeSignature("T3"),
                                new SimpleClassTypeSignature("T4"))));
    }

    @Test
    void canParseClassTypeSignatureGeneric() {
        assertThat(parser.parseTypeSignature("LFoo<LBar;>;"))
                .isEqualTo(new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("",
                        new SimpleClassTypeSignature("Foo",
                                List.of(new SimpleClassTypeSignature.TypeArgument.Reference(
                                        new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("",
                                                new SimpleClassTypeSignature("Bar")))))));
    }

    @Test
    void canParseClassTypeSignatureGenericWildcardTypeVariable() {
        var typeArgs = List.<SimpleClassTypeSignature.TypeArgument>of(
                new SimpleClassTypeSignature.TypeArgument.Reference(
                        new JavaTypeSignature.ReferenceTypeSignature.TypeVariableSignature("V"),
                        SimpleClassTypeSignature.WildCardIndicator.PLUS),
                new SimpleClassTypeSignature.TypeArgument.Reference(
                        new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("",
                                new SimpleClassTypeSignature("Bar")),
                        SimpleClassTypeSignature.WildCardIndicator.MINUS)
        );

        assertThat(parser.parseTypeSignature("La/b/C<+TV;-LBar;>;"))
                .isEqualTo(new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("a.b",
                        new SimpleClassTypeSignature("C", typeArgs)));
    }

    @Test
    void canParseClassTypeSignatureGenericWithStar() {
        assertThat(parser.parseTypeSignature("La/b/C<*>;"))
                .isEqualTo(new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("a.b",
                        new SimpleClassTypeSignature("C", List.of(
                                SimpleClassTypeSignature.TypeArgument.Star.INSTANCE))));
    }

    @Test
    void canParseClassTypeSignatureGenericNestedGeneric() {
        var nestedTypeArgs = List.<SimpleClassTypeSignature.TypeArgument>of(
                new SimpleClassTypeSignature.TypeArgument.Reference(
                        new JavaTypeSignature.ReferenceTypeSignature.TypeVariableSignature("Generic"),
                        SimpleClassTypeSignature.WildCardIndicator.PLUS),
                SimpleClassTypeSignature.TypeArgument.Star.INSTANCE,
                new SimpleClassTypeSignature.TypeArgument.Reference(
                        new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("java.util",
                                new SimpleClassTypeSignature("List")),
                        SimpleClassTypeSignature.WildCardIndicator.MINUS)
        );

        assertThat(parser.parseTypeSignature("La/b/C<*>.D<+TGeneric;*-Ljava/util/List;>.E;"))
                .isEqualTo(new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("a.b",
                        new SimpleClassTypeSignature("C", List.of(
                                SimpleClassTypeSignature.TypeArgument.Star.INSTANCE)),
                        List.of(new SimpleClassTypeSignature("D", nestedTypeArgs),
                                new SimpleClassTypeSignature("E"))));
    }

    @Test
    void canParseClassSignature() {
        assertThat(parser.parseClassSignature("Ljava/lang/Object;"))
                .isEqualTo(new ClassSignature(List.of(),
                        new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("java.lang",
                                new SimpleClassTypeSignature("Object"))));
    }

    @Test
    void canParseClassSignatureWithInterface() {
        assertThat(parser.parseClassSignature("Ljava/lang/Object;Lrunner/Run;"))
                .isEqualTo(new ClassSignature(List.of(),
                        new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("java.lang",
                                new SimpleClassTypeSignature("Object")),
                        List.of(new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("runner",
                                new SimpleClassTypeSignature("Run")))));
    }

    @Test
    void canParseClassSignatureWithInterfaces() {
        assertThat(parser.parseClassSignature("LS;LA;LB;LC;"))
                .isEqualTo(new ClassSignature(List.of(),
                        new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("",
                                new SimpleClassTypeSignature("S")),
                        List.of(new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("",
                                        new SimpleClassTypeSignature("A")),
                                new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("",
                                        new SimpleClassTypeSignature("B")),
                                new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("",
                                        new SimpleClassTypeSignature("C")))));
    }

    @Test
    void canParseClassSignatureGeneric() {
        assertThat(parser.parseClassSignature("<A:>Ljava/lang/Object;"))
                .isEqualTo(new ClassSignature(List.of(new TypeParameter("A")),
                        new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("java.lang",
                                new SimpleClassTypeSignature("Object"))));
    }

    @Test
    void canParseClassSignatureGenericWithBound() {
        assertThat(parser.parseClassSignature("<Foo:[Z>LA;"))
                .isEqualTo(new ClassSignature(List.of(new TypeParameter("Foo",
                        new JavaTypeSignature.ReferenceTypeSignature.ArrayTypeSignature((short) 1,
                                JavaTypeSignature.BaseType.Z))),
                        new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("",
                                new SimpleClassTypeSignature("A"))));
    }

    @Test
    void canParseClassSignatureGenericWithOnlyInterfaceBounds() {
        assertThat(parser.parseClassSignature("<F::Ljava/lang/Runnable;:Lanother/Interface;>LA;"))
                .isEqualTo(new ClassSignature(List.of(new TypeParameter("F", null,
                        List.of(new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("java.lang",
                                        new SimpleClassTypeSignature("Runnable")),
                                new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("another",
                                        new SimpleClassTypeSignature("Interface"))))),
                        new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("",
                                new SimpleClassTypeSignature("A"))));
    }

    @Test
    void canParseClassSignatureGenericWithClassBoundAndInterfaceBound() {
        assertThat(parser.parseClassSignature("<F:Lsome/Bound;:Lanother/Interface;>LA;"))
                .isEqualTo(new ClassSignature(List.of(new TypeParameter("F",
                        new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("some",
                                new SimpleClassTypeSignature("Bound")),
                        List.of(new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("another",
                                new SimpleClassTypeSignature("Interface"))))),
                        new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("",
                                new SimpleClassTypeSignature("A"))));
    }

    @Test
    void canParseClassSignatureGenericWithClassBoundAndInterfaceBounds() {
        assertThat(parser.parseClassSignature("<F:Lsome/Bound;:Lanother/Interface;:Lone/More;>LA;"))
                .isEqualTo(new ClassSignature(List.of(new TypeParameter("F",
                        new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("some",
                                new SimpleClassTypeSignature("Bound")),
                        List.of(new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("another",
                                        new SimpleClassTypeSignature("Interface")),
                                new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("one",
                                        new SimpleClassTypeSignature("More"))))),
                        new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("",
                                new SimpleClassTypeSignature("A"))));
    }

    @Test
    void canParseClassSignatureGenericTypeParameters() {
        assertThat(parser.parseClassSignature("<Foo:Ljava/lang/Object;Bar:>LA;"))
                .isEqualTo(new ClassSignature(List.of(
                        new TypeParameter("Foo", new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("java.lang",
                                new SimpleClassTypeSignature("Object"))),
                        new TypeParameter("Bar")),
                        new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("",
                                new SimpleClassTypeSignature("A"))));
    }

    @Test
    void canParseClassSignatureGenericTypeParametersWithBounds() {
        assertThat(parser.parseClassSignature("<Foo::Ljava/util/List;Bar::Ljava/lang/Runnable;F:>LA;"))
                .isEqualTo(new ClassSignature(List.of(
                        new TypeParameter("Foo", null, List.of(new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("java.util",
                                new SimpleClassTypeSignature("List")))),
                        new TypeParameter("Bar", null, List.of(new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("java.lang",
                                new SimpleClassTypeSignature("Runnable")))),
                        new TypeParameter("F")),
                        new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("",
                                new SimpleClassTypeSignature("A"))));
    }

}
