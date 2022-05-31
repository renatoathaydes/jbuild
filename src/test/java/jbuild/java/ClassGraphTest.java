package jbuild.java;

import jbuild.java.code.Code;
import jbuild.java.code.Definition;
import jbuild.log.JBuildLog;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static jbuild.TestSystemProperties.myClassesJar;
import static jbuild.TestSystemProperties.otherClassesJar;
import static jbuild.java.code.Code.Method.Instruction.invokestatic;
import static jbuild.java.code.Code.Method.Instruction.invokevirtual;
import static jbuild.java.code.Code.Method.Instruction.other;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class ClassGraphTest {

    private static ClassGraph classGraph;

    @BeforeAll
    static void beforeAll() throws Exception {
        var loader = JarSetPermutations.create(
                new JBuildLog(new PrintStream(new ByteArrayOutputStream()), false));

        var graphs = loader.fromJars(
                        otherClassesJar,
                        myClassesJar).toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        if (graphs.size() != 1) fail("Expected a single ClassGraph: " + graphs);

        classGraph = graphs.get(0).toClassGraph().toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(classGraph.getTypesByJar().keySet())
                .isEqualTo(Set.of(myClassesJar, otherClassesJar));
    }

    @AfterAll
    static void afterAll() {
        classGraph = null;
    }

    @Test
    void canCheckThatDefaultConstructorExists() {
        assertThat(classGraph.exists("Lfoo/Bar;",
                new Definition.MethodDefinition("\"<init>\"", "()V"))
        ).isTrue();
    }

    @Test
    void canCheckThatMethodDoesNotExistInClass() {
        assertThat(classGraph.exists("Lfoo/Bar;",
                new Definition.MethodDefinition("not", "()V"))
        ).isFalse();
    }

    @Test
    void testVirtualMethodInvocationCanBeFound() {
        assertThat(classGraph.findImplementation(
                new Code.Method("Lfoo/SomethingSpecific;", "some", "()Ljava/lang/String;", invokevirtual))
        ).isNotNull();
        // non-invokevirtual call shouldn't be found
        assertThat(classGraph.findImplementation(
                new Code.Method("Lfoo/SomethingSpecific;", "some", "()Ljava/lang/String;", invokestatic))
        ).isNull();
    }

    @Test
    void testJavaVirtualMethodInvocationCanBeFound() {
        assertThat(classGraph.findImplementation(
                new Code.Method("Ljava/util/HashMap;", "equals", "(Ljava/lang/Object;)Z", invokevirtual))
        ).isNotNull().isEmpty();
        // non-invokevirtual call shouldn't be found
        assertThat(classGraph.findImplementation(
                new Code.Method("Ljava/util/HashMap;", "equals", "(Ljava/lang/Object;)Z", invokestatic))
        ).isNull();
    }

    @Test
    void testJavaConstructorExists() {
        assertThat(classGraph.findImplementation(
                new Code.Method("Ljava/util/HashMap;", "\"<init>\"", "(I)V", other))
        ).isNotNull().isEmpty();
        // constructor cannot be invoked with invokevirtual
        assertThat(classGraph.findImplementation(
                new Code.Method("Ljava/util/HashMap;", "\"<init>\"", "(I)V", invokevirtual))
        ).isNull();
        // constructor just doesn't exist
        assertThat(classGraph.findImplementation(
                new Code.Method("Ljava/util/HashMap;", "\"<init>\"", "(F)V", other))
        ).isNull();
    }

    @Test
    void canCheckFieldExistsInClass() {
        assertThat(classGraph.exists("Lfoo/Fields;",
                new Definition.FieldDefinition("aString", "Ljava/lang/String;"))
        ).isTrue();

        assertThat(classGraph.exists("Lfoo/Fields;",
                new Definition.FieldDefinition("aBoolean", "Z"))
        ).isTrue();

        assertThat(classGraph.exists("Lfoo/Fields;",
                new Definition.FieldDefinition("aChar", "C"))
        ).isFalse();
    }

    @Test
    void canCheckMethodExistsInClass() {
        // method defined in BaseA itself
        assertThat(classGraph.exists("Lgenerics/BaseA;",
                new Definition.MethodDefinition("aBoolean", "()Z"))
        ).isTrue();
    }

    @Test
    void canCheckMethodExistsInSuperClass() {
        // method defined in super-class of BaseA
        assertThat(classGraph.exists("Lgenerics/BaseA;",
                new Definition.MethodDefinition("string", "()Ljava/lang/String;"))
        ).isTrue();
    }

    @Test
    void canCheckMethodExistsInSuperInterface() {
        assertThat(classGraph.exists("Lfoo/MultiInterface;",
                new Definition.MethodDefinition("run", "()V"))
        ).isTrue();
    }

    @Test
    void javaPrimitiveTypesAlwaysExist() {
        var examples = List.of("B", "C", "D", "F", "I", "J", "S", "V", "Z", "[Z", "[[B");
        for (String example : examples) {
            assertThat(classGraph.existsJava(example)).withFailMessage(example + " should exist").isTrue();
        }
    }

    @Test
    void javaStdLibTypesAlwaysExist() {
        var examples = List.of(
                "Ljava/lang/String;",
                "\"[Ljava/lang/String;\"",
                "Ljava/util/List;",
                "Ljavax/security/auth/x500/X500Principal;",
                "Lcom/sun/crypto/provider/SunJCE;",
                "[[Lcom/sun/crypto/provider/SunJCE;");
        for (String example : examples) {
            assertThat(classGraph.existsJava(example)).withFailMessage(example + " should exist").isTrue();
            assertThat(classGraph.exists(example)).withFailMessage(example + " should exist").isTrue();
        }
    }

    @Test
    void canCheckMethodExistsInJavaClass() {
        assertThat(classGraph.exists("\"[D\"",
                new Definition.MethodDefinition("clone", "()Ljava/lang/Object;"))
        ).isTrue();
    }

    @Test
    void canCheckMethodExistsInJavaArrayType() {
        assertThat(classGraph.exists("\"[D\"",
                new Definition.MethodDefinition("clone", "()Ljava/lang/Object;"))
        ).isTrue();

        assertThat(classGraph.exists("\"[Ljava/lang/Number;\"",
                new Definition.MethodDefinition("clone", "()Ljava/lang/Object;"))
        ).isTrue();

        assertThat(classGraph.exists("\"[Ljava/lang/Number;\"",
                new Definition.MethodDefinition("foo", "()Ljava/lang/Object;"))
        ).isFalse();
    }

    @Test
    void canCheckMethodExistsInArrays() {
        assertThat(classGraph.existsJava("Ljavax/security/auth/x500/X500Principal;",
                new Definition.MethodDefinition("getName", "(Ljava/lang/String;)Ljava/lang/String;"))
        ).isTrue();
    }

    @Test
    void canCheckFieldExistsInJavaType() {
        assertThat(classGraph.existsJava("Ljavax/security/auth/x500/X500Principal;",
                new Definition.FieldDefinition("RFC1779", "Ljava/lang/String;"))
        ).isTrue();

        assertThat(classGraph.existsJava("Ljavax/security/auth/x500/X500Principal;",
                new Definition.FieldDefinition("FOO_BAR", "Ljava/lang/String;"))
        ).isFalse();
    }

    @Test
    void canCheckFieldExistsInJavaArrayType() {
        assertThat(classGraph.exists("\"[I\"",
                new Definition.FieldDefinition("length", "I"))
        ).isTrue();

        assertThat(classGraph.exists("\"[[Ljava/lang/Object;\"",
                new Definition.FieldDefinition("length", "I"))
        ).isTrue();

        assertThat(classGraph.exists("\"[I\"",
                new Definition.FieldDefinition("FOO_BAR", "Ljava/lang/String;"))
        ).isFalse();
    }

    @Test
    void canCheckConstructorExistsInJavaType() {
        assertThat(classGraph.existsJava("Ljavax/security/auth/x500/X500Principal;",
                new Definition.MethodDefinition("\"<init>\"", "(Ljava/lang/String;)V"))
        ).isTrue();

        assertThat(classGraph.existsJava("Ljavax/security/auth/x500/X500Principal;",
                new Definition.MethodDefinition("\"<init>\"", "(I)V"))
        ).isFalse();
    }
}
