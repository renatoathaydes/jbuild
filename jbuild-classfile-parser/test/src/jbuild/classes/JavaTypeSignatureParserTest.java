package jbuild.classes;

import jbuild.classes.signature.JavaTypeSignature;
import jbuild.classes.signature.SimpleClassTypeSignature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaTypeSignatureParserTest {
    private final JavaTypeSignatureParser parser = new JavaTypeSignatureParser();

    @Test
    void canParseBaseTypeSignature() {
        for (var value : JavaTypeSignature.BaseType.values()) {
            assertThat(parser.parseTypeSignature(value.name())).isEqualTo(value);
        }
    }

    @Test
    void canParseSimpleClassTypeSignature() {
        assertThat(parser.parseTypeSignature("Ljava/lang/Object;"))
                .isEqualTo(new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("java.lang",
                        new SimpleClassTypeSignature("Object")));
    }

    @Test
    void canParseClassTypeSignatureWithSuffix() {
        assertThat(parser.parseTypeSignature("Lorg/T1.T2;"))
                .isEqualTo(new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("org",
                        new SimpleClassTypeSignature("T1"), List.of(new SimpleClassTypeSignature("T2"))));

        assertThat(parser.parseTypeSignature("Lorg/T1.T2.T3.T4;"))
                .isEqualTo(new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("org",
                        new SimpleClassTypeSignature("T1"),
                        List.of(new SimpleClassTypeSignature("T2"), new SimpleClassTypeSignature("T3"),
                                new SimpleClassTypeSignature("T4"))));
    }

    @Test
    void canParseClassTypeSignatureGeneric() {
        assertThat(parser.parseTypeSignature("LFoo<LBar;>;"))
                .isEqualTo(new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("",
                        new SimpleClassTypeSignature("Foo",
                                List.of(new SimpleClassTypeSignature.TypeArgument.Reference(
                                        new JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature("",
                                                new SimpleClassTypeSignature("Bar")))))));
    }
}
