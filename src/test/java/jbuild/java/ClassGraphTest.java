package jbuild.java;

import jbuild.commands.FixCommandExecutor;
import jbuild.java.code.Code;
import jbuild.java.code.MethodDefinition;
import jbuild.log.JBuildLog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassGraphTest {

    static final String otherClassesJar = System.getProperty("tests.other-test-classes.jar");

    private static ClassGraph classGraph;

    @BeforeAll
    static void beforeAll() {
        var fix = new FixCommandExecutor(new JBuildLog(new PrintStream(new ByteArrayOutputStream()), false));
        classGraph = fix.parseClassDefinitionsInJars(new File(otherClassesJar), new File(JavapOutputParserTest.myClassesJar));
    }

    @Test
    void canFindReferencesToType() {
        var to = new Code.Type("Lfoo/Bar;");

        assertThat(classGraph.referencesTo(to)).containsExactlyInAnyOrderElementsOf(Set.of(
                new CodeReference(otherClassesJar, "Lother/UsesBar;",
                        new MethodDefinition("foo", "()V"), to),
                new CodeReference(otherClassesJar, "Lother/UsesBar;",
                        new MethodDefinition("foo", "()V"),
                        new Code.Method("Lfoo/Bar;", "\"<init>\"", "()V")),
                new CodeReference(otherClassesJar, "Lother/CallsZortToCreateBar;",
                        new MethodDefinition("Lother/CallsZortToCreateBar;", "()V"), to),
                new CodeReference(otherClassesJar, "Lother/CallsZortToCreateBar;",
                        new MethodDefinition("Lother/CallsZortToCreateBar;", "()V"),
                        new Code.Method("Lfoo/Bar;", "\"<init>\"", "()V")),
                new CodeReference(otherClassesJar, "Lother/ReadsFieldOfZort;",
                        new MethodDefinition("b", "(Lfoo/Bar;)V"), to),
                new CodeReference(otherClassesJar, "Lother/ReadsFieldOfZort;",
                        new MethodDefinition("c", "(I)Lfoo/Bar;"), to),
                new CodeReference(otherClassesJar, "Lother/ExtendsBar;",
                        new MethodDefinition("Lother/ExtendsBar;", "()V"),
                        new Code.Method("Lfoo/Bar;", "\"<init>\"", "()V"))));

        to = new Code.Type("Lfoo/Zort;");

        assertThat(classGraph.referencesTo(to)).containsExactlyInAnyOrderElementsOf(Set.of(
                new CodeReference(otherClassesJar, "Lother/CallsZortToCreateBar;",
                        new MethodDefinition("Lother/CallsZortToCreateBar;", "()V"),
                        new Code.Method("Lfoo/Zort;", "getBar", "(Lfoo/Bar;)Lfoo/Bar;")),
                // Zort is referred to both in the type signature of "z" and in the body when it reads a field from Zort
                new CodeReference(otherClassesJar, "Lother/ReadsFieldOfZort;",
                        new MethodDefinition("z", "(Lfoo/Zort;)V"),
                        to),
                new CodeReference(otherClassesJar, "Lother/ReadsFieldOfZort;",
                        new MethodDefinition("z", "(Lfoo/Zort;)V"),
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
                        new MethodDefinition("doNothing", "([Lfoo/FunctionalCode;)V"), to),
                new CodeReference(otherClassesJar, "Lother/UsesArrayOfFunctionalCode;",
                        new MethodDefinition("makesArray", "()[Ljava/lang/Object;"), to)));
    }

    @Test
    @Disabled("not implemented yet, needs to handle synthetic class generated for enum")
    void canFindReferencesToEnums() {
        var to = new Code.Type("Lfoo/SomeEnum;");

        assertThat(classGraph.referencesTo(to)).containsExactlyInAnyOrderElementsOf(Set.of(
                new CodeReference(otherClassesJar, "Lother/UsesEnum;",
                        new MethodDefinition("checkEnum", "()V"), to)));
    }

    @Test
    void canFindReferencesToMethod() {
        var to = new Code.Method("Lfoo/Bar;", "\"<init>\"", "()V");

        assertThat(classGraph.referencesTo(to)).containsExactlyInAnyOrderElementsOf(Set.of(
                new CodeReference(otherClassesJar, "Lother/UsesBar;",
                        new MethodDefinition("foo", "()V"), to),
                new CodeReference(otherClassesJar, "Lother/ExtendsBar;",
                        new MethodDefinition("Lother/ExtendsBar;", "()V"), to),
                new CodeReference(otherClassesJar, "Lother/CallsZortToCreateBar;",
                        new MethodDefinition("Lother/CallsZortToCreateBar;", "()V"), to)));

        to = new Code.Method("Lfoo/Zort;", "getBar", "(Lfoo/Bar;)Lfoo/Bar;");

        assertThat(classGraph.referencesTo(to)).containsExactlyInAnyOrderElementsOf(Set.of(
                new CodeReference(otherClassesJar, "Lother/CallsZortToCreateBar;",
                        new MethodDefinition("Lother/CallsZortToCreateBar;", "()V"), to)));

    }

    @Test
    void canFindReferencesToField() {
        var to = new Code.Field("Lfoo/Zort;", "bar", "Lfoo/Bar;");

        assertThat(classGraph.referencesTo(to)).containsExactlyInAnyOrderElementsOf(Set.of(
                new CodeReference(otherClassesJar, "Lother/ReadsFieldOfZort;",
                        new MethodDefinition("z", "(Lfoo/Zort;)V"), to)));
    }

    @Test
    void canFindReferencesToMethodHandle() {
        var to = new Code.Method("Lfoo/ExampleLogger;", "debug", "(Ljava/lang/String;)V");

        assertThat(classGraph.referencesTo(to)).containsExactlyInAnyOrderElementsOf(Set.of(
                new CodeReference(otherClassesJar, "Lother/UsesMethodHandleFromExampleLogger;",
                        // the method definition where this is used is not currently known as it's from the constant table
                        null, to)));
    }
}
