package jbuild.util;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaTypeUtilsTest {

    @Test
    void canParseTypeParameters() {
        assertThat(JavaTypeUtils.parseMethodArgumentsTypes("(Lfoo/Bar;)"))
                .isEqualTo(List.of("Lfoo/Bar;"));
        assertThat(JavaTypeUtils.parseMethodArgumentsTypes("(Lfoo/Bar;"))
                .isEqualTo(List.of("Lfoo/Bar;"));
        assertThat(JavaTypeUtils.parseMethodArgumentsTypes("(BLfoo/Bar;"))
                .isEqualTo(List.of("B", "Lfoo/Bar;"));
        assertThat(JavaTypeUtils.parseMethodArgumentsTypes("(Lfoo/Bar;I"))
                .isEqualTo(List.of("Lfoo/Bar;", "I"));
        assertThat(JavaTypeUtils.parseMethodArgumentsTypes("(Lfoo/Bar;Ljava/lang/String;"))
                .isEqualTo(List.of("Lfoo/Bar;", "Ljava/lang/String;"));
        assertThat(JavaTypeUtils.parseMethodArgumentsTypes("(Lfoo/Bar;IBCDFIJSZ"))
                .isEqualTo(List.of("Lfoo/Bar;", "I", "B", "C", "D", "F", "I", "J", "S", "Z"));
        assertThat(JavaTypeUtils.parseMethodArgumentsTypes("ZLjava/lang/String;ILjava/util/List;Z"))
                .isEqualTo(List.of("Z", "Ljava/lang/String;", "I", "Ljava/util/List;", "Z"));
    }

    @Test
    void canParseArrayType() {
        assertThat(JavaTypeUtils.parseMethodArgumentsTypes("[Lfoo/Bar;"))
                .isEqualTo(List.of("[Lfoo/Bar;"));
        assertThat(JavaTypeUtils.parseMethodArgumentsTypes("[I"))
                .isEqualTo(List.of("[I"));
        assertThat(JavaTypeUtils.parseMethodArgumentsTypes("[[[Lfoo/Bar;[[[I"))
                .isEqualTo(List.of("[[[Lfoo/Bar;", "[[[I"));
    }

    @Test
    void canCleanArrayTypeName() {
        assertThat(JavaTypeUtils.cleanArrayTypeName("Lfoo/Bar;"))
                .isEqualTo("Lfoo/Bar;");
        assertThat(JavaTypeUtils.cleanArrayTypeName("[Lfoo/Bar;"))
                .isEqualTo("Lfoo/Bar;");
        assertThat(JavaTypeUtils.cleanArrayTypeName("[[Lfoo/Bar;"))
                .isEqualTo("Lfoo/Bar;");
        assertThat(JavaTypeUtils.cleanArrayTypeName("[[[Lfoo/Bar;"))
                .isEqualTo("Lfoo/Bar;");
        assertThat(JavaTypeUtils.cleanArrayTypeName("\"[[Lfoo/Bar;\""))
                .isEqualTo("Lfoo/Bar;");
    }

    @Test
    void classNameToTypeName() {
        assertThat(JavaTypeUtils.classNameToTypeName("int")).isEqualTo("I");
        assertThat(JavaTypeUtils.classNameToTypeName("float")).isEqualTo("F");
        assertThat(JavaTypeUtils.classNameToTypeName("java.lang.String")).isEqualTo("Ljava/lang/String;");
        assertThat(JavaTypeUtils.classNameToTypeName("Foo")).isEqualTo("LFoo;");
        assertThat(JavaTypeUtils.classNameToTypeName("int[]")).isEqualTo("[I");
        assertThat(JavaTypeUtils.classNameToTypeName("int[][][][]")).isEqualTo("[[[[I");
        assertThat(JavaTypeUtils.classNameToTypeName("Foo[]")).isEqualTo("[LFoo;");
        assertThat(JavaTypeUtils.classNameToTypeName("Foo[][]")).isEqualTo("[[LFoo;");
    }

    @Test
    void fileNameToTypeName() {
        assertThat(JavaTypeUtils.fileToTypeName("Foo.class")).isEqualTo("LFoo;");
        assertThat(JavaTypeUtils.fileToTypeName("p" + File.pathSeparator + "Foo.class")).isEqualTo("Lp/Foo;");
        assertThat(JavaTypeUtils.fileToTypeName(
            "pkg" + File.pathSeparator + "abc" + File.pathSeparator + "Some$Cls.class")
        ).isEqualTo("Lpkg/abc/Some$Cls;");
    }

    @Test
    void typeNameToClassName() {
        assertThat(JavaTypeUtils.typeNameToClassName("I")).isEqualTo("int");
        assertThat(JavaTypeUtils.typeNameToClassName("F")).isEqualTo("float");
        assertThat(JavaTypeUtils.typeNameToClassName("Ljava/lang/String;")).isEqualTo("java.lang.String");
        assertThat(JavaTypeUtils.typeNameToClassName("LFoo;")).isEqualTo("Foo");
        assertThat(JavaTypeUtils.typeNameToClassName("foo/Bar")).isEqualTo("foo.Bar");
        assertThat(JavaTypeUtils.typeNameToClassName("[I")).isEqualTo("int[]");
        assertThat(JavaTypeUtils.typeNameToClassName("[[[[I")).isEqualTo("int[][][][]");
        assertThat(JavaTypeUtils.typeNameToClassName("[LFoo;")).isEqualTo("Foo[]");
        assertThat(JavaTypeUtils.typeNameToClassName("[[LFoo;")).isEqualTo("Foo[][]");
    }

    @Test
    void toTypeDescriptor() {
        assertThat(JavaTypeUtils.toTypeDescriptor(int.class)).isEqualTo("I");
        assertThat(JavaTypeUtils.toTypeDescriptor(Integer.class)).isEqualTo("Ljava/lang/Integer;");
        assertThat(JavaTypeUtils.toTypeDescriptor(List.class)).isEqualTo("Ljava/util/List;");
        assertThat(JavaTypeUtils.toTypeDescriptor(int[].class)).isEqualTo("[I");
        assertThat(JavaTypeUtils.toTypeDescriptor(Long[].class)).isEqualTo("[Ljava/lang/Long;");
        assertThat(JavaTypeUtils.toTypeDescriptor(Boolean[][].class)).isEqualTo("[[Ljava/lang/Boolean;");
    }
}
