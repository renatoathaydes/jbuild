package jbuild.java;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaVersionHelperTest {
    @Test
    void canFindCurrentJavaVersion() {
        // this should not fail in any JVM
        JavaVersionHelper.currentJavaVersion();
    }

    @Test
    void canParseJavaVersion() {
        assertThat(JavaVersionHelper.parseJavaVersion("1.6")).isEqualTo(6);
        assertThat(JavaVersionHelper.parseJavaVersion("1.7.0")).isEqualTo(7);
        assertThat(JavaVersionHelper.parseJavaVersion("11.0.14")).isEqualTo(11);
        assertThat(JavaVersionHelper.parseJavaVersion("17.0.2")).isEqualTo(17);
        assertThat(JavaVersionHelper.parseJavaVersion("24.0.2")).isEqualTo(24);
        assertThat(JavaVersionHelper.parseJavaVersion("25-ea")).isEqualTo(25);
    }

}
