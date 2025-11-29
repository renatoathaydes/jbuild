package jbuild.util;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaTypeUtilsTest {

    @Test
    void canParseTypeDescriptor() {
        assertThat(JavaTypeUtils.parseTypeDescriptor("(Lfoo/Bar;)", true))
                .isEqualTo(List.of("Lfoo/Bar;"));
        assertThat(JavaTypeUtils.parseTypeDescriptor("(Lfoo/Bar;", true))
                .isEqualTo(List.of("Lfoo/Bar;"));
        assertThat(JavaTypeUtils.parseTypeDescriptor("(BLfoo/Bar;", true))
                .isEqualTo(List.of("B", "Lfoo/Bar;"));
        assertThat(JavaTypeUtils.parseTypeDescriptor("(Lfoo/Bar;I", true))
                .isEqualTo(List.of("Lfoo/Bar;", "I"));
        assertThat(JavaTypeUtils.parseTypeDescriptor("(Lfoo/Bar;Ljava/lang/String;", true))
                .isEqualTo(List.of("Lfoo/Bar;", "Ljava/lang/String;"));
        assertThat(JavaTypeUtils.parseTypeDescriptor("(Lfoo/Bar;IBCDFIJSZ", true))
                .isEqualTo(List.of("Lfoo/Bar;", "I", "B", "C", "D", "F", "I", "J", "S", "Z"));
        assertThat(JavaTypeUtils.parseTypeDescriptor("ZLjava/lang/String;ILjava/util/List;Z", true))
                .isEqualTo(List.of("Z", "Ljava/lang/String;", "I", "Ljava/util/List;", "Z"));
    }

    @Test
    void canParseArrayType() {
        assertThat(JavaTypeUtils.parseTypeDescriptor("[Lfoo/Bar;", true))
                .isEqualTo(List.of("[Lfoo/Bar;"));
        assertThat(JavaTypeUtils.parseTypeDescriptor("[I", true))
                .isEqualTo(List.of("[I"));
        assertThat(JavaTypeUtils.parseTypeDescriptor("[[[Lfoo/Bar;[[[I", true))
                .isEqualTo(List.of("[[[Lfoo/Bar;", "[[[I"));
    }

    @Test
    void canParseArrayTypeWithNoArrayRetention() {
        assertThat(JavaTypeUtils.parseTypeDescriptor("[Lfoo/Bar;", false))
                .isEqualTo(List.of("Lfoo/Bar;"));
        assertThat(JavaTypeUtils.parseTypeDescriptor("[I", false))
                .isEqualTo(List.of("I"));
        assertThat(JavaTypeUtils.parseTypeDescriptor("[[[Lfoo/Bar;[[[I", false))
                .isEqualTo(List.of("Lfoo/Bar;", "I"));
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
        final var s = File.separatorChar;

        assertThat(JavaTypeUtils.fileToTypeName("Foo.class")).isEqualTo("LFoo;");
        assertThat(JavaTypeUtils.fileToTypeName("p" + s + "Foo.class")).isEqualTo("Lp/Foo;");
        assertThat(JavaTypeUtils.fileToTypeName(
                "pkg" + s + "abc" + s + "Some$Cls.class")
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
