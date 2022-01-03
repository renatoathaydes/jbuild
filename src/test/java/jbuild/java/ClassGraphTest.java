package jbuild.java;

import jbuild.java.code.Code;
import jbuild.java.code.Definition;
import jbuild.log.JBuildLog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static jbuild.TestSystemProperties.myClassesJar;
import static jbuild.TestSystemProperties.otherClassesJar;
import static org.assertj.core.api.Assertions.assertThat;

public class ClassGraphTest {

    private static ClassGraph classGraph;

    @BeforeAll
    static void beforeAll() {
        var loader = ClassGraphLoader.create(
                new JBuildLog(new PrintStream(new ByteArrayOutputStream()), false));

        classGraph = loader.fromJars(new File(otherClassesJar), new File(myClassesJar));
    }

    @Test
    void canFindReferencesToType() {
        var to = new Code.Type("Lfoo/Bar;");

        assertThat(classGraph.referencesTo(to)).containsExactlyInAnyOrderElementsOf(Set.of(
                new CodeReference(otherClassesJar, "Lother/UsesBar;",
                        new Definition.MethodDefinition("foo", "()V"), to),
                new CodeReference(otherClassesJar, "Lother/UsesBar;",
                        new Definition.MethodDefinition("foo", "()V"),
                        new Code.Method("Lfoo/Bar;", "\"<init>\"", "()V")),
                new CodeReference(otherClassesJar, "Lother/CallsZortToCreateBar;",
                        new Definition.MethodDefinition("\"<init>\"", "()V"), to),
                new CodeReference(otherClassesJar, "Lother/CallsZortToCreateBar;",
                        new Definition.MethodDefinition("\"<init>\"", "()V"),
                        new Code.Method("Lfoo/Bar;", "\"<init>\"", "()V")),
                new CodeReference(otherClassesJar, "Lother/ReadsFieldOfZort;",
                        new Definition.MethodDefinition("b", "(Lfoo/Bar;)V"), to),
                new CodeReference(otherClassesJar, "Lother/ReadsFieldOfZort;",
                        new Definition.MethodDefinition("c", "(I)Lfoo/Bar;"), to),
                new CodeReference(otherClassesJar, "Lother/ExtendsBar;",
                        new Definition.MethodDefinition("\"<init>\"", "()V"),
                        new Code.Method("Lfoo/Bar;", "\"<init>\"", "()V")),
                new CodeReference(otherClassesJar, "Lother/ExtendsBar;",
                        null,
                        new Code.Type("Lfoo/Bar;"))));

        to = new Code.Type("Lfoo/Zort;");

        assertThat(classGraph.referencesTo(to)).containsExactlyInAnyOrderElementsOf(Set.of(
                new CodeReference(otherClassesJar, "Lother/CallsZortToCreateBar;",
                        new Definition.MethodDefinition("\"<init>\"", "()V"),
                        new Code.Method("Lfoo/Zort;", "getBar", "(Lfoo/Bar;)Lfoo/Bar;")),
                // Zort is referred to both in the type signature of "z" and in the body when it reads a field from Zort
                new CodeReference(otherClassesJar, "Lother/ReadsFieldOfZort;",
                        new Definition.MethodDefinition("z", "(Lfoo/Zort;)V"),
                        to),
                new CodeReference(otherClassesJar, "Lother/ReadsFieldOfZort;",
                        new Definition.MethodDefinition("z", "(Lfoo/Zort;)V"),
                        new Code.Field("Lfoo/Zort;", "bar", "Lfoo/Bar;"))));
    }

    @Test
    void canFindReferencesToTypeViaImplements() {
        var to = new Code.Type("Lfoo/EmptyInterface;");

        assertThat(classGraph.referencesTo(to)).containsExactlyInAnyOrderElementsOf(Set.of(
                new CodeReference(otherClassesJar, "Lother/ImplementsEmptyInterface;", null, to),
                new CodeReference(otherClassesJar, "Lother/UsesComplexType$Param;", null, to)));
    }

    @Test
    void canFindReferencesToTypeViaArray() {
        var to = new Code.Type("Lfoo/FunctionalCode;");

        assertThat(classGraph.referencesTo(to)).containsExactlyInAnyOrderElementsOf(Set.of(
                new CodeReference(otherClassesJar, "Lother/UsesArrayOfFunctionalCode;",
                        new Definition.MethodDefinition("doNothing", "([Lfoo/FunctionalCode;)V"), to),
                new CodeReference(otherClassesJar, "Lother/UsesArrayOfFunctionalCode;",
                        new Definition.MethodDefinition("makesArray", "()[Ljava/lang/Object;"), to)));
    }

    @Test
    void canFindReferencesToTypeViaUnusedField() {
        var to = new Code.Type("Lfoo/Something;");

        assertThat(classGraph.referencesTo(to)).containsExactlyInAnyOrderElementsOf(Set.of(
                new CodeReference(otherClassesJar, "Lother/HasSomething;",
                        new Definition.FieldDefinition("something", "Lfoo/Something;"), to),
                new CodeReference(otherClassesJar, "Lother/HasSomething;",
                        new Definition.FieldDefinition("mySomething", "Lfoo/Something;"), to),
                new CodeReference(otherClassesJar, "Lother/CallsSuperMethod;",
                        new Definition.MethodDefinition("call", "(Lfoo/Something;)Ljava/lang/String;"), to),
                new CodeReference(otherClassesJar, "Lother/CallsSuperMethod;",
                        new Definition.MethodDefinition("call", "(Lfoo/Something;)Ljava/lang/String;"),
                        new Code.Method("Lfoo/Something;", "some", "()Ljava/lang/String;"))));
    }

    @Test
    void canFindReferencesToEnums() {
        var to = new Code.Type("Lfoo/SomeEnum;");

        var allRefs = classGraph.referencesTo(to);

        // remove the synthetic class javac generates for some reason
        var result = allRefs.stream()
                .filter(ref -> !ref.type.endsWith("UsesEnum$1;"))
                .collect(toSet());

        assertThat(result).containsExactlyInAnyOrderElementsOf(Set.of(
                new CodeReference(otherClassesJar, "Lother/UsesEnum;",
                        new Definition.FieldDefinition("someEnum", "Lfoo/SomeEnum;"), to),
                new CodeReference(otherClassesJar, "Lother/UsesEnum;",
                        new Definition.MethodDefinition("checkEnum", "()V"),
                        new Code.Method("Lfoo/SomeEnum;", "ordinal", "()I"))));
    }

    @Test
    void canFindReferencesToMethod() {
        var to = new Code.Method("Lfoo/Bar;", "\"<init>\"", "()V");

        assertThat(classGraph.referencesTo(to)).containsExactlyInAnyOrderElementsOf(Set.of(
                new CodeReference(otherClassesJar, "Lother/UsesBar;",
                        new Definition.MethodDefinition("foo", "()V"), to),
                new CodeReference(otherClassesJar, "Lother/ExtendsBar;",
                        new Definition.MethodDefinition("\"<init>\"", "()V"), to),
                new CodeReference(otherClassesJar, "Lother/CallsZortToCreateBar;",
                        new Definition.MethodDefinition("\"<init>\"", "()V"), to)));

        to = new Code.Method("Lfoo/Zort;", "getBar", "(Lfoo/Bar;)Lfoo/Bar;");

        assertThat(classGraph.referencesTo(to)).containsExactlyInAnyOrderElementsOf(Set.of(
                new CodeReference(otherClassesJar, "Lother/CallsZortToCreateBar;",
                        new Definition.MethodDefinition("\"<init>\"", "()V"), to)));

    }

    @Test
    @Disabled("Can find reference through generic receiver, but not super-type yet")
    void canFindReferencesToMethodThroughSuperTypeAndGenericReceiver() {
        var to = new Code.Method("Lgenerics/Base;", "string", "()Ljava/lang/String;");

        // only refs from other jars can be found, hence Lgenerics/Generics.takeT is not found
        assertThat(classGraph.referencesTo(to)).containsExactlyInAnyOrderElementsOf(Set.of(
                new CodeReference(otherClassesJar, "Lother/UsesBaseA;",
                        new Definition.MethodDefinition("usesSuperMethod", "()V"), to),
                new CodeReference(otherClassesJar, "Lother/UsesBaseViaGenerics;",
                        new Definition.MethodDefinition("useBase", "()V"), to)));
    }

    @Test
    void canFindReferenceToVirtualMethod() {
        var to = new Code.Method("Lfoo/SomethingSpecific;", "some", "()Ljava/lang/String;");

        assertThat(classGraph.referencesTo(to)).containsExactlyInAnyOrderElementsOf(Set.of(
                new CodeReference(otherClassesJar, "Lother/CallsSuperMethod;",
                        new Definition.MethodDefinition("callSuperOf", "(Lfoo/SomethingSpecific;)Ljava/lang/String;"), to)));

        to = new Code.Method("Lfoo/Something;", "some", "()Ljava/lang/String;");

        assertThat(classGraph.referencesTo(to)).containsExactlyInAnyOrderElementsOf(Set.of(
                new CodeReference(otherClassesJar, "Lother/CallsSuperMethod;",
                        new Definition.MethodDefinition("call", "(Lfoo/Something;)Ljava/lang/String;"), to)));
    }

    @Test
    void canFindReferencesToField() {
        var to = new Code.Field("Lfoo/Zort;", "bar", "Lfoo/Bar;");

        assertThat(classGraph.referencesTo(to)).containsExactlyInAnyOrderElementsOf(Set.of(
                new CodeReference(otherClassesJar, "Lother/ReadsFieldOfZort;",
                        new Definition.MethodDefinition("z", "(Lfoo/Zort;)V"), to)));
    }

    @Test
    void canFindReferencesToMethodHandle() {
        var to = new Code.Method("Lfoo/ExampleLogger;", "debug", "(Ljava/lang/String;)V");

        assertThat(classGraph.referencesTo(to)).containsExactlyInAnyOrderElementsOf(Set.of(
                new CodeReference(otherClassesJar, "Lother/UsesMethodHandleFromExampleLogger;",
                        // the method definition where this is used is not currently known as it's from the constant table
                        null, to)));
    }

    @Test
    void canFindOutIfReferenceToMethodExists() {
        var bar = classGraph.getTypesByJar().get(myClassesJar).get("Lfoo/Bar;");

        assertThat(classGraph.exists(myClassesJar, bar,
                new Definition.MethodDefinition("\"<init>\"", "()V"))
        ).isTrue();

        assertThat(classGraph.exists(myClassesJar, bar,
                new Definition.MethodDefinition("not", "()V"))
        ).isFalse();
    }

    @Test
    void canFindOutIfReferenceToFieldExists() {
        var fields = classGraph.getTypesByJar().get(myClassesJar).get("Lfoo/Fields;");
        assertThat(classGraph.exists(myClassesJar, fields,
                new Definition.FieldDefinition("aString", "Ljava/lang/String;"))
        ).isTrue();

        assertThat(classGraph.exists(myClassesJar, fields,
                new Definition.FieldDefinition("aBoolean", "Z"))
        ).isTrue();

        assertThat(classGraph.exists(myClassesJar, fields,
                new Definition.FieldDefinition("aChar", "C"))
        ).isFalse();
    }

    @Test
    void canFindReferenceToMethodInSuperType() {
        var baseA = classGraph.getTypesByJar().get(myClassesJar)
                .get("Lgenerics/BaseA;");

        // method defined in BaseA itself
        assertThat(classGraph.exists(myClassesJar, baseA,
                new Definition.MethodDefinition("aBoolean", "()Z"))
        ).isTrue();

        // method defined in super-class of BaseA
        assertThat(classGraph.exists(myClassesJar, baseA,
                new Definition.MethodDefinition("string", "()Ljava/lang/String;"))
        ).isTrue();
    }

    @Test
    void canFindReferenceToMethodInJavaSuperType() {
        var multiInterface = classGraph.getTypesByJar().get(myClassesJar)
                .get("Lfoo/MultiInterface;");

        assertThat(classGraph.exists(myClassesJar, multiInterface,
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
        }
    }

    @Test
    void canCheckMethodExistsInJavaType() {
        assertThat(classGraph.existsJava("Ljavax/security/auth/x500/X500Principal;",
                new Definition.MethodDefinition("getName", "(Ljava/lang/String;)Ljava/lang/String;"))
        ).isTrue();

        assertThat(classGraph.existsJava("Ljavax/security/auth/x500/X500Principal;",
                new Definition.MethodDefinition("getName", "(I)Ljava/lang/String;"))
        ).isFalse();
    }

    @Test
    void canCheckFieldExistsInJavaType() {
        assertThat(classGraph.existsJava("Ljavax/security/auth/x500/X500Principal;",
                new Definition.FieldDefinition("RFC1779", "Ljava/lang/String;"))
        ).isTrue();

        assertThat(classGraph.existsJava("Ljavax/security/auth/x500/X500Principal;",
                new Definition.MethodDefinition("FOO_BAR", "Ljava/lang/String;"))
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
