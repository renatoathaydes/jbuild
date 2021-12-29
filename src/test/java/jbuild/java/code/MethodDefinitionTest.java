package jbuild.java.code;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodDefinitionTest {

    @Test
    void canFindReturnType() {
        var method = new Definition.MethodDefinition("foo", "()V");
        assertThat(method.getReturnType()).isEqualTo("V");

        method = new Definition.MethodDefinition("foo", "(BC)I");
        assertThat(method.getReturnType()).isEqualTo("I");

        method = new Definition.MethodDefinition("foo", "(Ljava/lang/String;I)Ljava/lang/Character;");
        assertThat(method.getReturnType()).isEqualTo("Ljava/lang/Character;");
    }

    @Test
    void canFindParameterTypes() {
        var method = new Definition.MethodDefinition("foo", "()V");
        assertThat(method.getParameterTypes()).isEmpty();

        method = new Definition.MethodDefinition("foo", "(BC)I");
        assertThat(method.getParameterTypes()).isEqualTo(List.of("B", "C"));

        method = new Definition.MethodDefinition("foo", "(Ljava/lang/String;I)Ljava/lang/Character;");
        assertThat(method.getParameterTypes()).isEqualTo(List.of("Ljava/lang/String;", "I"));
    }
}