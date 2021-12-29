package jbuild.java;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaTypeParserTest {

    private final JavaTypeParser parser = new JavaTypeParser();

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

    @Disabled("cannot yet parse generic parameter which is itself generic")
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
                        List.of(typeParam("T", typeBound("Lgenerics/Generics;",
                                        typeParam("?", typeBound("Lgenerics/BaseA;"))),
                                typeBound("Lfoo/EmptyInterface;"))),
                        List.of(typeBound("Ljava/util/concurrent/Callable",
                                        typeParam("Lgenerics/Generics;", typeBound("generics/BaseA;"))),
                                typeBound("Ljava/lang/Runnable;"),
                                typeBound("Ljava/util/function/Function;",
                                        typeParam("Ljava/lang/String;"),
                                        typeParam("Lgenerics/Generics")))));
    }

    // public abstract class generics.X<T extends generics.Generics<? extends generics.BaseA> & foo.EmptyInterface> extends generics.Generics<generics.Base> implements java.util.concurrent.Callable<generics.Generics<generics.BaseA>>, java.lang.Runnable, java.util.function.Function<java.lang.String, generics.Generics<generics.Base>>

    private static JavaType.TypeParam typeParam(String name, JavaType.TypeBound... bounds) {
        return new JavaType.TypeParam(name, List.of(bounds));
    }

    private static JavaType.TypeBound typeBound(String name, JavaType.TypeParam... params) {
        return new JavaType.TypeBound(name, List.of(params));
    }

}
