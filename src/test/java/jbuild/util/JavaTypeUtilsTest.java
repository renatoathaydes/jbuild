package jbuild.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaTypeUtilsTest {

    @Test
    void canParseTypeParameters() {
        assertThat(JavaTypeUtils.parseTypes("(Lfoo/Bar;)"))
                .isEqualTo(List.of("Lfoo/Bar;"));
        assertThat(JavaTypeUtils.parseTypes("(Lfoo/Bar;"))
                .isEqualTo(List.of("Lfoo/Bar;"));
        assertThat(JavaTypeUtils.parseTypes("(BLfoo/Bar;"))
                .isEqualTo(List.of("B", "Lfoo/Bar;"));
        assertThat(JavaTypeUtils.parseTypes("(Lfoo/Bar;I"))
                .isEqualTo(List.of("Lfoo/Bar;", "I"));
        assertThat(JavaTypeUtils.parseTypes("(Lfoo/Bar;Ljava/lang/String;"))
                .isEqualTo(List.of("Lfoo/Bar;", "Ljava/lang/String;"));
        assertThat(JavaTypeUtils.parseTypes("(Lfoo/Bar;IBCDFIJSZ"))
                .isEqualTo(List.of("Lfoo/Bar;", "I", "B", "C", "D", "F", "I", "J", "S", "Z"));
        assertThat(JavaTypeUtils.parseTypes("ZLjava/lang/String;ILjava/util/List;Z"))
                .isEqualTo(List.of("Z", "Ljava/lang/String;", "I", "Ljava/util/List;", "Z"));
    }
}
