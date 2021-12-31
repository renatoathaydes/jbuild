package jbuild.java;

import jbuild.java.code.Code;
import jbuild.java.code.Definition;
import jbuild.log.JBuildLog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

public class ClassGraphTest {

    static final String otherClassesJar = System.getProperty("tests.other-test-classes.jar");

    private static ClassGraph classGraph;

    @BeforeAll
    static void beforeAll() {
        var loader = ClassGraphLoader.create(
                new JBuildLog(new PrintStream(new ByteArrayOutputStream()), false));

        classGraph = loader.fromJars(new File(otherClassesJar), new File(JavapOutputParserTest.myClassesJar));
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
                        new Definition.MethodDefinition("Lother/CallsZortToCreateBar;", "()V"), to),
                new CodeReference(otherClassesJar, "Lother/CallsZortToCreateBar;",
                        new Definition.MethodDefinition("Lother/CallsZortToCreateBar;", "()V"),
                        new Code.Method("Lfoo/Bar;", "\"<init>\"", "()V")),
                new CodeReference(otherClassesJar, "Lother/ReadsFieldOfZort;",
                        new Definition.MethodDefinition("b", "(Lfoo/Bar;)V"), to),
                new CodeReference(otherClassesJar, "Lother/ReadsFieldOfZort;",
                        new Definition.MethodDefinition("c", "(I)Lfoo/Bar;"), to),
                new CodeReference(otherClassesJar, "Lother/ExtendsBar;",
                        new Definition.MethodDefinition("Lother/ExtendsBar;", "()V"),
                        new Code.Method("Lfoo/Bar;", "\"<init>\"", "()V"))));

        to = new Code.Type("Lfoo/Zort;");

        assertThat(classGraph.referencesTo(to)).containsExactlyInAnyOrderElementsOf(Set.of(
                new CodeReference(otherClassesJar, "Lother/CallsZortToCreateBar;",
                        new Definition.MethodDefinition("Lother/CallsZortToCreateBar;", "()V"),
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
                new CodeReference(otherClassesJar, "Lother/ImplementsEmptyInterface;", null, to)));
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
                        new Definition.MethodDefinition("Lother/ExtendsBar;", "()V"), to),
                new CodeReference(otherClassesJar, "Lother/CallsZortToCreateBar;",
                        new Definition.MethodDefinition("Lother/CallsZortToCreateBar;", "()V"), to)));

        to = new Code.Method("Lfoo/Zort;", "getBar", "(Lfoo/Bar;)Lfoo/Bar;");

        assertThat(classGraph.referencesTo(to)).containsExactlyInAnyOrderElementsOf(Set.of(
                new CodeReference(otherClassesJar, "Lother/CallsZortToCreateBar;",
                        new Definition.MethodDefinition("Lother/CallsZortToCreateBar;", "()V"), to)));

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
        var bar = classGraph.getTypesByJar().get(JavapOutputParserTest.myClassesJar).get("Lfoo/Bar;");

        assertThat(classGraph.refExists(JavapOutputParserTest.myClassesJar, bar,
                new Definition.MethodDefinition("Lfoo/Bar;", "()V"))
        ).isTrue();

        assertThat(classGraph.refExists(JavapOutputParserTest.myClassesJar, bar,
                new Definition.MethodDefinition("not", "()V"))
        ).isFalse();
    }

    @Test
    void canFindOutIfReferenceToFieldExists() {
        var fields = classGraph.getTypesByJar().get(JavapOutputParserTest.myClassesJar).get("Lfoo/Fields;");
        System.out.println(fields);
        assertThat(classGraph.refExists(JavapOutputParserTest.myClassesJar, fields,
                new Definition.FieldDefinition("aString", "Ljava/lang/String;"))
        ).isTrue();

        assertThat(classGraph.refExists(JavapOutputParserTest.myClassesJar, fields,
                new Definition.FieldDefinition("aBoolean", "Z"))
        ).isTrue();

        assertThat(classGraph.refExists(JavapOutputParserTest.myClassesJar, fields,
                new Definition.FieldDefinition("aChar", "C"))
        ).isFalse();
    }
}
