package jbuild.java;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JavaTypeParserTest {

    private final JavaTypeParser parser = new JavaTypeParser(true);

    @Test
    void canParseBasicClass() {
        var type = parser.parse("class Hello");

        assertThat(type)
                .isEqualTo(new JavaType(new JavaType.TypeId("LHello;", JavaType.Kind.CLASS),
                        List.of(), List.of(), List.of()));
    }

    @Test
    void canParseBasicInterface() {
        var type = parser.parse("interface Super");

        assertThat(type)
                .isEqualTo(new JavaType(new JavaType.TypeId("LSuper;", JavaType.Kind.INTERFACE)
                        , List.of(), List.of(), List.of()));
    }

    @Test
    void canParseBasicEnum() {
        var type = parser.parseSignature(new JavaType.TypeId("Lfoo/SomeEnum;", JavaType.Kind.ENUM),
                "Ljava/lang/Enum<Lfoo/SomeEnum;>;");

        assertThat(type)
                .isEqualTo(new JavaType(new JavaType.TypeId("Lfoo/SomeEnum;", JavaType.Kind.ENUM),
                        List.of(typeBound("Ljava/lang/Enum;", typeParam("Lfoo/SomeEnum;"))),
                        List.of(), List.of()));
    }

    @Test
    void canParseClassExtendingAnother() {
        var type = parser.parse("public class foo.SomethingSpecific extends foo.Something");

        assertThat(type)
                .isEqualTo(new JavaType(new JavaType.TypeId("Lfoo/SomethingSpecific;", JavaType.Kind.CLASS),
                        List.of(typeBound("Lfoo/Something;")), List.of(), List.of()));
    }

    @Test
    void canParseInterfaceExtendingOthers() {
        var type = parser.parse("public interface foo.MultiInterface" +
                " extends java.lang.Runnable,java.io.Closeable,java.lang.AutoCloseable");

        assertThat(type)
                .isEqualTo(new JavaType(new JavaType.TypeId("Lfoo/MultiInterface;", JavaType.Kind.INTERFACE),
                        List.of(), List.of(),
                        List.of(typeBound("Ljava/lang/Runnable;"),
                                typeBound("Ljava/io/Closeable;"),
                                typeBound("Ljava/lang/AutoCloseable;"))));
    }

    @Test
    void canParseClassWithSimpleTypeParameter() {
        var type = parser.parseSignature(
                new JavaType.TypeId("Lgenerics/BasicGenerics;", JavaType.Kind.CLASS),
                "<T:Ljava/lang/Object;>Ljava/lang/Object;");

        assertThat(type)
                .isEqualTo(new JavaType(new JavaType.TypeId("Lgenerics/BasicGenerics;", JavaType.Kind.CLASS),
                        List.of(),
                        List.of(typeParam("T")),
                        List.of()));
    }

    @Test
    void canParseClassWithBoundedTypeParameter() {
        var type = parser.parseSignature(
                new JavaType.TypeId("LGenerics;", JavaType.Kind.CLASS),
                "<T:Lgenerics/Base;>Ljava/lang/Object;");

        assertThat(type)
                .isEqualTo(new JavaType(new JavaType.TypeId("LGenerics;", JavaType.Kind.CLASS),
                        List.of(),
                        List.of(typeParam("T", typeBound("Lgenerics/Base;"))),
                        List.of()));
    }

    @Test
    void canParseClassImplementingInterfaces() {
        var type = parser.parse("public class other.ImplementsEmptyInterface" +
                " implements foo.EmptyInterface,java.lang.Runnable");

        assertThat(type)
                .isEqualTo(new JavaType(new JavaType.TypeId("Lother/ImplementsEmptyInterface;", JavaType.Kind.CLASS),
                        List.of(),
                        List.of(),
                        List.of(typeBound("Lfoo/EmptyInterface;"), typeBound("Ljava/lang/Runnable;"))));
    }

    @Test
    void canParseClassWithMultiplyBoundedTypeParameter() {
        var type = parser.parseSignature(
                new JavaType.TypeId("Lgenerics/MultipleBounds;", JavaType.Kind.CLASS),
                "<T:Lgenerics/Generics<+Lgenerics/BaseA;>;:Lfoo/EmptyInterface;>Ljava/lang/Object;");

        assertThat(type)
                .isEqualTo(new JavaType(new JavaType.TypeId("Lgenerics/MultipleBounds;", JavaType.Kind.CLASS),
                        List.of(),
                        List.of(typeParam("T",
                                typeBound("Lgenerics/Generics;", typeParam("Lgenerics/BaseA;")),
                                typeBound("Lfoo/EmptyInterface;"))),
                        List.of()));
    }

    @Test
    void canParseClassWithRecursiveBoundedTypeParameter() {
        var type = parser.parseSignature(
                new JavaType.TypeId("Lgenerics/GenericParameter;", JavaType.Kind.CLASS),
                "<T:Ljava/lang/Object;V:Ljava/lang/Object;>" +
                        "Ljava/lang/Object;" +
                        "Ljava/util/function/Function<Lgenerics/Generics<+Lgenerics/BaseA;>;Ljava/util/concurrent/Callable<TT;>;>;");

        assertThat(type)
                .isEqualTo(new JavaType(new JavaType.TypeId("Lgenerics/GenericParameter;", JavaType.Kind.CLASS),
                        List.of(),
                        List.of(typeParam("T"), typeParam("V")),
                        List.of(typeBound("Ljava/util/function/Function;",
                                typeParam("Lgenerics/Generics;",
                                        List.of(), typeParam("Lgenerics/BaseA;")),
                                typeParam("Ljava/util/concurrent/Callable;",
                                        List.of(), typeParam("TT"))))));
    }

    @Test
    void canParseClassWithManyTypeParameters() {
        var type = parser.parseSignature(
                new JavaType.TypeId("Lgenerics/ManyGenerics;", JavaType.Kind.CLASS),
                // javap output is missing the final '>'
                "<A:Ljava/lang/Object;B:Ljava/lang/Object;C:Ljava/lang/Object;D:Ljava/lang/Object;>Ljava/lang/Object;"
        );

        assertThat(type)
                .isEqualTo(new JavaType(new JavaType.TypeId("Lgenerics/ManyGenerics;", JavaType.Kind.CLASS),
                        List.of(),
                        List.of(typeParam("A"), typeParam("B"),
                                typeParam("C"), typeParam("D")),
                        List.of()));
    }

    @Test
    void canParseComplexType() {
        var type = parser.parseSignature(
                new JavaType.TypeId("Lgenerics/ComplexType;", JavaType.Kind.CLASS),
                "<T:Lgenerics/Generics<+Lgenerics/BaseA;>;:Lfoo/EmptyInterface;>" +
                        "Lgenerics/Generics<Lgenerics/Base;>;" +
                        "Ljava/util/concurrent/Callable<Lgenerics/Generics<Lgenerics/BaseA;>;>;" +
                        "Ljava/lang/Runnable;" +
                        "Ljava/util/function/Function<Ljava/lang/String;Lgenerics/Generics<Lgenerics/Base;>;>;");

        assertThat(type)
                .isEqualTo(new JavaType(new JavaType.TypeId("Lgenerics/ComplexType;", JavaType.Kind.CLASS),
                        List.of(typeBound("Lgenerics/Generics;",
                                typeParam("Lgenerics/Base;"))),
                        List.of(typeParam("T",
                                typeBound("Lgenerics/Generics;",
                                        typeParam("Lgenerics/BaseA;")),
                                typeBound("Lfoo/EmptyInterface;"))),
                        List.of(typeBound("Ljava/util/concurrent/Callable;",
                                        typeParam("Lgenerics/Generics;", List.of(), typeParam("Lgenerics/BaseA;"))),
                                typeBound("Ljava/lang/Runnable;"),
                                typeBound("Ljava/util/function/Function;",
                                        typeParam("Ljava/lang/String;"),
                                        typeParam("Lgenerics/Generics;", List.of(), typeParam("Lgenerics/Base;"))))));
    }

    @Test
    void canParseGenericTypeWithinGenericType() {
        var type = parser.parseSignature(
                new JavaType.TypeId("Lgenerics/GenericStructure$OtherData;", JavaType.Kind.CLASS),
                "Lgenerics/GenericStructure<TD;>.Data<TD;>;");

        assertThat(type)
                .isEqualTo(new JavaType(new JavaType.TypeId("Lgenerics/GenericStructure$OtherData;", JavaType.Kind.CLASS),
                        List.of(typeBound("Lgenerics/GenericStructure$Data;",
                                typeParam("TD"))),
                        List.of(),
                        List.of()));
    }

    @Test
    void canParseGenericTypeWithArray() {
        var type = parser.parseSignature(
                new JavaType.TypeId("Lgenerics/GenericWithArray;", JavaType.Kind.CLASS),
                "Ljava/lang/Object;Lgenerics/GenericParameter<[Ljava/lang/Boolean;[[Ljava/lang/String;>;");

        assertThat(type)
                .isEqualTo(new JavaType(new JavaType.TypeId("Lgenerics/GenericWithArray;", JavaType.Kind.CLASS),
                        List.of(),
                        List.of(),
                        List.of(typeBound("Lgenerics/GenericParameter;",
                                typeParam("[Ljava/lang/Boolean;"), typeParam("[[Ljava/lang/String;")))));
    }

    @Test
    void canParseReallyComplexGuavaType() {
        // final class com.google.common.util.concurrent.AbstractCatchingFuture$CatchingFuture
        // <V extends java.lang.Object,
        //  X extends java.lang.Throwable>
        //  extends com.google.common.util.concurrent.AbstractCatchingFuture
        //  <V, X, com.google.common.base.Function<? super X, ? extends V>, V>
        var type = parser.parseSignature(
                new JavaType.TypeId("Lcom/google/common/util/concurrent/AbstractCatchingFuture$CatchingFuture;", JavaType.Kind.CLASS),
                "<V:Ljava/lang/Object;X:Ljava/lang/Throwable;>" +
                        "Lcom/google/common/util/concurrent/AbstractCatchingFuture" +
                        "<TV;TX;Lcom/google/common/base/Function<-TX;+TV;>;TV;>;");

        assertThat(type)
                .isEqualTo(new JavaType(new JavaType.TypeId("Lcom/google/common/util/concurrent/AbstractCatchingFuture$CatchingFuture;", JavaType.Kind.CLASS),
                        List.of(typeBound("Lcom/google/common/util/concurrent/AbstractCatchingFuture;",
                                typeParam("TV"),
                                typeParam("TX"),
                                typeParam("Lcom/google/common/base/Function;", List.of(),
                                        typeParam("TX"), typeParam("TV")),
                                typeParam("TV"))),
                        List.of(typeParam("V"), typeParam("X", typeBound("Ljava/lang/Throwable;"))),
                        List.of()));
    }

    @Test
    void canParseTypeWithGenericBoundaryBeingAnotherGenericParameter() {
        // final class com.google.common.base.PairwiseEquivalence
        //  <E extends java.lang.Object, T extends E>
        //  extends com.google.common.base.Equivalence<java.lang.Iterable<T>>
        //  implements java.io.Serializable
        var type = parser.parseSignature(
                new JavaType.TypeId("Lcom/google/common/base/PairwiseEquivalence;", JavaType.Kind.CLASS),
                "<E:Ljava/lang/Object;T:TE;>" +
                        "Lcom/google/common/base/Equivalence<Ljava/lang/Iterable<TT;>;>;" +
                        "Ljava/io/Serializable;");

        assertThat(type)
                .isEqualTo(new JavaType(new JavaType.TypeId("Lcom/google/common/base/PairwiseEquivalence;", JavaType.Kind.CLASS),
                        List.of(typeBound("Lcom/google/common/base/Equivalence;",
                                typeParam("Ljava/lang/Iterable;", List.of(), typeParam("TT")))),
                        List.of(typeParam("E"), typeParam("T", typeBound("TE"))),
                        List.of(typeBound("Ljava/io/Serializable;"))));
    }

    @Test
    void canThrowErrorsOrReturnNullWhenInvalidLineIsParsed() {
        class Example {
            final String line;
            final String expectedError;

            public Example(String line, String expectedError) {
                this.line = line;
                this.expectedError = expectedError;
            }
        }

        var examples = List.of(
                new Example("", "no type name found"),
                new Example("foo", "expected word followed by space but got 'foo\u0000'"),
                new Example("foo bar", "expected a type kind or modifier but got 'foo'"),
                new Example("public bar", "expected word followed by space but got 'bar\u0000'"),
                new Example("public bar ", "expected a type kind or modifier but got 'bar'"),
                new Example("public class ", "expected type bound but got '\u0000' at index 13"),
                new Example("class Foo extends ", "expected type bound but got '\u0000' at index 18"),
                new Example("class Foo implements ", "expected type bound but got '\u0000' at index 21"),
                new Example("class Foo<>", "unexpected input following type at index 9"),
                new Example("class Foo extends Bar extends Zort",
                        "unexpected input following type at index 22"),
                new Example("class Foo extends Bar implements Zort extends Boo",
                        "unexpected input following type at index 38"),
                new Example("class Foo123 implements Abc$123, DEF!",
                        "unexpected input following type at index 36"),
                new Example("interface Inter implements A",
                        "unexpected input following type at index 16")
        );

        // parser throws Exceptions on errors
        for (var example : examples) {
            assertThatThrownBy(() -> parser.parse(example.line),
                    "example: '" + example.line + "'")
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage(example.expectedError);
        }

        // lenient parser, returns null on errors
        final var lenientParser = new JavaTypeParser(false);
        for (var example : examples) {
            assertThat(lenientParser.parse(example.line)).isNull();
        }
    }

    private static JavaType.TypeParam typeParam(String name, JavaType.TypeBound... bounds) {
        return new JavaType.TypeParam(name, List.of(bounds), List.of());
    }

    private static JavaType.TypeParam typeParam(String name,
                                                List<JavaType.TypeBound> bounds,
                                                JavaType.TypeParam... params) {
        return new JavaType.TypeParam(name, bounds, List.of(params));
    }

    private static JavaType.TypeBound typeBound(String name, JavaType.TypeParam... params) {
        return new JavaType.TypeBound(name, List.of(params));
    }

}
