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
                .isEqualTo(new JavaType("LHello;", JavaType.OBJECT, List.of(), List.of()));
    }

    @Test
    void canParseClassExtendingAnother() {
        var type = parser.parse("public class foo.SomethingSpecific extends foo.Something");

        assertThat(type)
                .isEqualTo(new JavaType("Lfoo/SomethingSpecific;",
                        typeBound("Lfoo/Something;"), List.of(), List.of()));
    }

    @Test
    void canParseClassWithSimpleTypeParameter() {
        var type = parser.parse("class generics.BasicGenerics<T extends java.lang.Object> extends java.lang.Object");

        assertThat(type)
                .isEqualTo(new JavaType("Lgenerics/BasicGenerics;",
                        JavaType.OBJECT,
                        List.of(typeParam("T")),
                        List.of()));
    }

    @Test
    void canParseClassWithBoundedTypeParameter() {
        var type = parser.parse("class Generics<T extends BaseA>");

        assertThat(type)
                .isEqualTo(new JavaType("LGenerics;",
                        JavaType.OBJECT,
                        List.of(typeParam("T", typeBound("LBaseA;"))),
                        List.of()));
    }

    @Test
    void canParseClassImplementingInterfaces() {
        var type = parser.parse("public class other.ImplementsEmptyInterface" +
                " implements foo.EmptyInterface,java.lang.Runnable");

        assertThat(type)
                .isEqualTo(new JavaType("Lother/ImplementsEmptyInterface;",
                        JavaType.OBJECT,
                        List.of(),
                        List.of(typeBound("Lfoo/EmptyInterface;"), typeBound("Ljava/lang/Runnable;"))));
    }

    @Test
    void canParseClassWithMultiplyBoundedTypeParameter() {
        var type = parser.parse("class Generics<" +
                "T extends Other<? extends generics.BaseA> " +
                "& foo.EmptyInterface>");

        assertThat(type)
                .isEqualTo(new JavaType("LGenerics;",
                        JavaType.OBJECT,
                        List.of(typeParam("T",
                                typeBound("LOther;", typeParam("?", typeBound("Lgenerics/BaseA;"))),
                                typeBound("Lfoo/EmptyInterface;"))),
                        List.of()));
    }

    @Test
    void canParseClassWithRecursiveBoundedTypeParameter() {
        var type = parser.parse("class Generics<T extends Generic<? extends bar.Foo>>");

        assertThat(type)
                .isEqualTo(new JavaType("LGenerics;",
                        JavaType.OBJECT,
                        List.of(typeParam("T",
                                typeBound("LGeneric;",
                                        typeParam("?", typeBound("Lbar/Foo;"))))),
                        List.of()));
    }

    @Test
    void canParseClassWithManyTypeParameters() {
        var type = parser.parse("public class generics.ManyGenerics<" +
                "A extends java.lang.Object, " +
                "B extends java.lang.Object, " +
                "C extends java.lang.Object, " +
                "D extends java.lang.Object> " +
                "extends java.lang.Object");

        assertThat(type)
                .isEqualTo(new JavaType("Lgenerics/ManyGenerics;",
                        JavaType.OBJECT,
                        List.of(typeParam("A"), typeParam("B"),
                                typeParam("C"), typeParam("D")),
                        List.of()));
    }

    @Test
    void canParseComplexType() {
        var type = parser.parse("public abstract class generics.X<" +
                "T extends generics.Generics<? extends generics.BaseA> & foo.EmptyInterface> " +
                "extends generics.Generics<generics.Base> " +
                "implements java.util.concurrent.Callable<generics.Generics<generics.BaseA>>," +
                " java.lang.Runnable," +
                " java.util.function.Function<java.lang.String, generics.Generics<generics.Base>>");

        assertThat(type)
                .isEqualTo(new JavaType("Lgenerics/X;",
                        typeBound("Lgenerics/Generics;",
                                typeParam("Lgenerics/Base;")),
                        List.of(typeParam("T",
                                typeBound("Lgenerics/Generics;",
                                        typeParam("?", typeBound("Lgenerics/BaseA;"))),
                                typeBound("Lfoo/EmptyInterface;"))),
                        List.of(typeBound("Ljava/util/concurrent/Callable;",
                                        typeParam("Lgenerics/Generics;", List.of(), typeParam("Lgenerics/BaseA;"))),
                                typeBound("Ljava/lang/Runnable;"),
                                typeBound("Ljava/util/function/Function;",
                                        typeParam("Ljava/lang/String;"),
                                        typeParam("Lgenerics/Generics;", List.of(), typeParam("Lgenerics/Base;"))))));
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
                new Example("public class ", "expected type bound but got '\u0000'"),
                new Example("class Foo extends ", "expected type bound but got '\u0000'"),
                new Example("class Foo implements ", "expected type bound but got '\u0000'"),
                new Example("class Foo<>", "expected type parameter but got '>'"),
                new Example("class Foo< >", "expected type parameter but got ' '"),
                new Example("class Foo<Bar", "expected type parameters to end with '>' but got '\u0000'"),
                new Example("class Foo<Bar Zort>", "expected type parameters to end with '>' but got 'Z'"),
                new Example("class Foo<Bar implements Zort>",
                        "expected type parameters to end with '>' but got 'i'"),
                new Example("class Foo extends Bar extends Zort",
                        "unexpected input following type at index 22"),
                new Example("class Foo extends Bar implements Zort extends Boo",
                        "unexpected input following type at index 38"),
                new Example("class Foo123 implements Abc$123, DEF!",
                        "unexpected input following type at index 36")
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
