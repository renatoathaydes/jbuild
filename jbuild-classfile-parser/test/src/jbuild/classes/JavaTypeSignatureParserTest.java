package jbuild.classes;

import jbuild.classes.parser.JavaTypeSignatureParser;
import jbuild.classes.signature.ClassSignature;
import jbuild.classes.signature.JavaTypeSignature;
import jbuild.classes.signature.JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature;
import jbuild.classes.signature.JavaTypeSignature.ReferenceTypeSignature.TypeVariableSignature;
import jbuild.classes.signature.MethodSignature;
import jbuild.classes.signature.MethodSignature.MethodResult.MethodReturnType;
import jbuild.classes.signature.MethodSignature.MethodResult.VoidDescriptor;
import jbuild.classes.signature.SimpleClassTypeSignature;
import jbuild.classes.signature.SimpleClassTypeSignature.WildCardIndicator;
import jbuild.classes.signature.TypeParameter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaTypeSignatureParserTest {

    public static final ClassTypeSignature OBJECT = new ClassTypeSignature("java/lang",
            new SimpleClassTypeSignature("", "Object"));

    public static final ClassTypeSignature LIST = new ClassTypeSignature("java/util",
            new SimpleClassTypeSignature("", "List"));

    private final JavaTypeSignatureParser parser = new jbuild.classes.parser.JavaTypeSignatureParser();

    @Test
    void canParseBaseTypeSignature() {
        for (var value : JavaTypeSignature.BaseType.values()) {
            assertThat(parser.parseJavaTypeSignature(value.name())).isEqualTo(value);
        }
    }

    @Test
    void canParseArrayTypeSignature() {
        assertThat(parser.parseJavaTypeSignature("[Ljava/lang/Object;"))
                .isEqualTo(new JavaTypeSignature.ReferenceTypeSignature.ArrayTypeSignature((short) 1, OBJECT));
    }

    @Test
    void canParseArrayTypeSignaturePrimitive() {
        assertThat(parser.parseJavaTypeSignature("[B"))
                .isEqualTo(new JavaTypeSignature.ReferenceTypeSignature.ArrayTypeSignature((short) 1,
                        JavaTypeSignature.BaseType.B));
        assertThat(parser.parseJavaTypeSignature("[[Z"))
                .isEqualTo(new JavaTypeSignature.ReferenceTypeSignature.ArrayTypeSignature((short) 2,
                        JavaTypeSignature.BaseType.Z));
        assertThat(parser.parseJavaTypeSignature("[[[D"))
                .isEqualTo(new JavaTypeSignature.ReferenceTypeSignature.ArrayTypeSignature((short) 3,
                        JavaTypeSignature.BaseType.D));
    }

    @Test
    void canParseSimpleClassTypeSignature() {
        assertThat(parser.parseJavaTypeSignature("Ljava/lang/Object;")).isEqualTo(OBJECT);
    }

    @Test
    void canParseSimpleTypeVariableSignature() {
        assertThat(parser.parseJavaTypeSignature("TB;")).isEqualTo(
                new JavaTypeSignature.ReferenceTypeSignature.TypeVariableSignature("B"));
    }

    @Test
    void canParseClassTypeSignatureWithSuffix() {
        assertThat(parser.parseJavaTypeSignature("Lorg/T1.T2;"))
                .isEqualTo(new ClassTypeSignature("org",
                        new SimpleClassTypeSignature("", "T1"), List.of(new SimpleClassTypeSignature("", "T2"))));

        assertThat(parser.parseJavaTypeSignature("Lorg/T1.T2.T3.T4;"))
                .isEqualTo(new ClassTypeSignature("org",
                        new SimpleClassTypeSignature("", "T1"),
                        List.of(new SimpleClassTypeSignature("", "T2"), new SimpleClassTypeSignature("", "T3"),
                                new SimpleClassTypeSignature("", "T4"))));
    }

    @Test
    void canParseClassTypeSignatureGeneric() {
        assertThat(parser.parseJavaTypeSignature("LFoo<LBar;>;"))
                .isEqualTo(new ClassTypeSignature("",
                        new SimpleClassTypeSignature("", "Foo",
                                List.of(new SimpleClassTypeSignature.TypeArgument.Reference(
                                        new ClassTypeSignature("",
                                                new SimpleClassTypeSignature("", "Bar")))))));
    }

    @Test
    void canParseClassTypeSignatureGenericWildcardTypeVariable() {
        var typeArgs = List.<SimpleClassTypeSignature.TypeArgument>of(
                new SimpleClassTypeSignature.TypeArgument.Reference(
                        new TypeVariableSignature("V"),
                        WildCardIndicator.PLUS),
                new SimpleClassTypeSignature.TypeArgument.Reference(
                        new ClassTypeSignature("",
                                new SimpleClassTypeSignature("", "Bar")),
                        WildCardIndicator.MINUS)
        );

        assertThat(parser.parseJavaTypeSignature("La/b/C<+TV;-LBar;>;"))
                .isEqualTo(new ClassTypeSignature("a/b",
                        new SimpleClassTypeSignature("", "C", typeArgs)));
    }

    @Test
    void canParseClassTypeSignatureGenericWithStar() {
        assertThat(parser.parseJavaTypeSignature("La/b/C<*>;"))
                .isEqualTo(new ClassTypeSignature("a/b",
                        new SimpleClassTypeSignature("", "C", List.of(
                                SimpleClassTypeSignature.TypeArgument.Star.INSTANCE))));
    }

    @Test
    void canParseClassTypeSignatureGenericNestedGeneric() {
        var nestedTypeArgs = List.of(
                new SimpleClassTypeSignature.TypeArgument.Reference(
                        new TypeVariableSignature("Generic"),
                        WildCardIndicator.PLUS),
                SimpleClassTypeSignature.TypeArgument.Star.INSTANCE,
                new SimpleClassTypeSignature.TypeArgument.Reference(
                        LIST,
                        WildCardIndicator.MINUS)
        );

        assertThat(parser.parseJavaTypeSignature("La/b/C<*>.D<+TGeneric;*-Ljava/util/List;>.E;"))
                .isEqualTo(new ClassTypeSignature("a/b",
                        new SimpleClassTypeSignature("", "C", List.of(
                                SimpleClassTypeSignature.TypeArgument.Star.INSTANCE)),
                        List.of(new SimpleClassTypeSignature("", "D", nestedTypeArgs),
                                new SimpleClassTypeSignature("", "E"))));
    }

    @Test
    void canParseClassSignature() {
        assertThat(parser.parseClassSignature("Ljava/lang/Object;"))
                .isEqualTo(new ClassSignature("", List.of(), OBJECT));
    }

    @Test
    void canParseClassSignatureWithInterface() {
        assertThat(parser.parseClassSignature("Ljava/lang/Object;Lrunner/Run;"))
                .isEqualTo(new ClassSignature("", List.of(),
                        OBJECT,
                        List.of(new ClassTypeSignature("runner",
                                new SimpleClassTypeSignature("", "Run")))));
    }

    @Test
    void canParseClassSignatureWithInterfaces() {
        assertThat(parser.parseClassSignature("LS;LA;LB;LC;"))
                .isEqualTo(new ClassSignature("", List.of(),
                        new ClassTypeSignature("",
                                new SimpleClassTypeSignature("", "S")),
                        List.of(new ClassTypeSignature("",
                                        new SimpleClassTypeSignature("", "A")),
                                new ClassTypeSignature("",
                                        new SimpleClassTypeSignature("", "B")),
                                new ClassTypeSignature("",
                                        new SimpleClassTypeSignature("", "C")))));
    }

    @Test
    void canParseClassSignatureGeneric() {
        assertThat(parser.parseClassSignature("<A:>Ljava/lang/Object;"))
                .isEqualTo(new ClassSignature("", List.of(new TypeParameter("A")),
                        OBJECT));
    }

    @Test
    void canParseClassSignatureGenericWithBound() {
        assertThat(parser.parseClassSignature("<Foo:[Z>LA;"))
                .isEqualTo(new ClassSignature("", List.of(new TypeParameter("Foo",
                        new JavaTypeSignature.ReferenceTypeSignature.ArrayTypeSignature((short) 1,
                                JavaTypeSignature.BaseType.Z))),
                        new ClassTypeSignature("",
                                new SimpleClassTypeSignature("", "A"))));
    }

    @Test
    void canParseClassSignatureGenericWithOnlyInterfaceBounds() {
        assertThat(parser.parseClassSignature("<F::Ljava/lang/Runnable;:Lanother/Interface;>LA;"))
                .isEqualTo(new ClassSignature("", List.of(new TypeParameter("F", null,
                        List.of(new ClassTypeSignature("java/lang",
                                        new SimpleClassTypeSignature("", "Runnable")),
                                new ClassTypeSignature("another",
                                        new SimpleClassTypeSignature("", "Interface"))))),
                        new ClassTypeSignature("",
                                new SimpleClassTypeSignature("", "A"))));
    }

    @Test
    void canParseClassSignatureGenericWithClassBoundAndInterfaceBound() {
        assertThat(parser.parseClassSignature("<F:Lsome/Bound;:Lanother/Interface;>LA;"))
                .isEqualTo(new ClassSignature("", List.of(new TypeParameter("F",
                        new ClassTypeSignature("some",
                                new SimpleClassTypeSignature("", "Bound")),
                        List.of(new ClassTypeSignature("another",
                                new SimpleClassTypeSignature("", "Interface"))))),
                        new ClassTypeSignature("",
                                new SimpleClassTypeSignature("", "A"))));
    }

    @Test
    void canParseClassSignatureGenericWithClassBoundAndInterfaceBounds() {
        assertThat(parser.parseClassSignature("<F:Lsome/Bound;:Lanother/Interface;:Lone/More;>LA;"))
                .isEqualTo(new ClassSignature("", List.of(new TypeParameter("F",
                        new ClassTypeSignature("some",
                                new SimpleClassTypeSignature("", "Bound")),
                        List.of(new ClassTypeSignature("another",
                                        new SimpleClassTypeSignature("", "Interface")),
                                new ClassTypeSignature("one",
                                        new SimpleClassTypeSignature("", "More"))))),
                        new ClassTypeSignature("",
                                new SimpleClassTypeSignature("", "A"))));
    }

    @Test
    void canParseClassSignatureGenericTypeParameters() {
        assertThat(parser.parseClassSignature("<Foo:Ljava/lang/Object;Bar:>LA;"))
                .isEqualTo(new ClassSignature("", List.of(
                        new TypeParameter("Foo", OBJECT),
                        new TypeParameter("Bar")),
                        new ClassTypeSignature("",
                                new SimpleClassTypeSignature("", "A"))));
    }

    @Test
    void canParseClassSignatureGenericTypeParametersWithBounds() {
        assertThat(parser.parseClassSignature("<Foo::Ljava/util/List;Bar::Ljava/lang/Runnable;F:>LA;"))
                .isEqualTo(new ClassSignature("", List.of(
                        new TypeParameter("Foo", null, List.of(LIST)),
                        new TypeParameter("Bar", null, List.of(new ClassTypeSignature("java/lang",
                                new SimpleClassTypeSignature("", "Runnable")))),
                        new TypeParameter("F")),
                        new ClassTypeSignature("",
                                new SimpleClassTypeSignature("", "A"))));
    }

    @Test
    void canParseBasicMethodSignature() {
        assertThat(parser.parseMethodSignature("()V")).isEqualTo(new MethodSignature("",
                List.of(), VoidDescriptor.INSTANCE));
    }

    @Test
    void canParseSimpleMethodSignature() {
        assertThat(parser.parseMethodSignature("()Z")).isEqualTo(new MethodSignature("",
                List.of(), new MethodReturnType(JavaTypeSignature.BaseType.Z)));
    }

    @Test
    void canParseMethodSignatureWithPrimitiveArgs() {
        assertThat(parser.parseMethodSignature("(B)Z")).isEqualTo(new MethodSignature("",
                List.of(JavaTypeSignature.BaseType.B),
                new MethodReturnType(JavaTypeSignature.BaseType.Z)));

        assertThat(parser.parseMethodSignature("(BJ)I")).isEqualTo(new MethodSignature("",
                List.of(JavaTypeSignature.BaseType.B, JavaTypeSignature.BaseType.J),
                new MethodReturnType(JavaTypeSignature.BaseType.I)));
    }

    @Test
    void canParseMethodSignatureWithReferenceTypes() {
        assertThat(parser.parseMethodSignature("(Ljava/lang/Object;)Ljava/util/List;"))
                .isEqualTo(new MethodSignature("", List.of(OBJECT),
                        new MethodReturnType(LIST)));

        assertThat(parser.parseMethodSignature("(Ljava/lang/Object;B)Ljava/util/List;"))
                .isEqualTo(new MethodSignature("", List.of(OBJECT, JavaTypeSignature.BaseType.B),
                        new MethodReturnType(LIST)));
    }

    @Test
    void canParseSimpleMethodSignatureThrows() {
        assertThat(parser.parseMethodSignature("()V^Lthrowable/Ex;"))
                .isEqualTo(new MethodSignature("", List.of(), List.of(), VoidDescriptor.INSTANCE,
                        List.of(new MethodSignature.ThrowsSignature.Class(
                                new ClassTypeSignature("throwable", new SimpleClassTypeSignature("", "Ex"))
                        ))));
    }

    @Test
    void canParseMethodSignatureThrowsMany() {
        assertThat(parser.parseMethodSignature("()V^Lthrowable/Ex;^LMore;^LLast;"))
                .isEqualTo(new MethodSignature("", List.of(), List.of(), VoidDescriptor.INSTANCE,
                        List.of(new MethodSignature.ThrowsSignature.Class(
                                new ClassTypeSignature("throwable", new SimpleClassTypeSignature("", "Ex"))
                        ), new MethodSignature.ThrowsSignature.Class(
                                new ClassTypeSignature("", new SimpleClassTypeSignature("", "More"))
                        ), new MethodSignature.ThrowsSignature.Class(
                                new ClassTypeSignature("", new SimpleClassTypeSignature("", "Last"))
                        ))));
    }

    @Test
    void canParseMethodSignatureThrowsGeneric() {
        assertThat(parser.parseMethodSignature("()V^TT;"))
                .isEqualTo(new MethodSignature("", List.of(), List.of(), VoidDescriptor.INSTANCE,
                        List.of(new MethodSignature.ThrowsSignature.TypeVariable(
                                new TypeVariableSignature("T")
                        ))));
    }

    @Test
    void canParseMethodSignatureThrowsGenerics() {
        assertThat(parser.parseMethodSignature("()V^TT;^TE;"))
                .isEqualTo(new MethodSignature("", List.of(), List.of(), VoidDescriptor.INSTANCE,
                        List.of(new MethodSignature.ThrowsSignature.TypeVariable(
                                new TypeVariableSignature("T")
                        ), new MethodSignature.ThrowsSignature.TypeVariable(
                                new TypeVariableSignature("E")
                        ))));
    }

    @Test
    void canParseMethodSignatureThrowsGenericAndReferenceType() {
        assertThat(parser.parseMethodSignature("()V^TT;^Lerror/Error;"))
                .isEqualTo(new MethodSignature("", List.of(), List.of(), VoidDescriptor.INSTANCE,
                        List.of(new MethodSignature.ThrowsSignature.TypeVariable(
                                new TypeVariableSignature("T")
                        ), new MethodSignature.ThrowsSignature.Class(
                                new ClassTypeSignature("error", new SimpleClassTypeSignature("", "Error"))
                        ))));
    }

    @Test
    void canParseGenericMethodSignature() {
        assertThat(parser.parseMethodSignature("<A:Ljava/lang/Object;>()Z")).isEqualTo(new MethodSignature("",
                List.of(new TypeParameter("A", OBJECT)), List.of(),
                new MethodReturnType(JavaTypeSignature.BaseType.Z), List.of()));
    }

    @Test
    void canParseGenericMethodSignatureWithArgs() {
        assertThat(parser.parseMethodSignature("<A:Ljava/lang/Object;>(BJ)Z")).isEqualTo(new MethodSignature("",
                List.of(new TypeParameter("A", OBJECT)),
                List.of(JavaTypeSignature.BaseType.B, JavaTypeSignature.BaseType.J),
                new MethodReturnType(JavaTypeSignature.BaseType.Z), List.of()));
    }

    @Test
    void canParseGenericMethodSignatureWithManyParameters() {
        assertThat(parser.parseMethodSignature("<A:Ljava/lang/Object;Another::LRunnable;>(B)Z")).isEqualTo(new MethodSignature("",
                List.of(new TypeParameter("A", OBJECT), new TypeParameter("Another", null,
                        List.of(new ClassTypeSignature("", new SimpleClassTypeSignature("", "Runnable"))))),
                List.of(JavaTypeSignature.BaseType.B),
                new MethodReturnType(JavaTypeSignature.BaseType.Z), List.of()));
    }

}
