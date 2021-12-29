package jbuild.util;

import org.junit.jupiter.api.Test;

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
                .isEqualTo(List.of("Lfoo/Bar;"));
        assertThat(JavaTypeUtils.parseMethodArgumentsTypes("[I"))
                .isEqualTo(List.of("I"));
        assertThat(JavaTypeUtils.parseMethodArgumentsTypes("[[[Lfoo/Bar;[[[I"))
                .isEqualTo(List.of("Lfoo/Bar;", "I"));
    }
}
