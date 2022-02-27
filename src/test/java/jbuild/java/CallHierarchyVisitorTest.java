package jbuild.java;

import jbuild.java.code.Code;
import jbuild.java.code.Definition;
import jbuild.log.JBuildLog;
import jbuild.util.Describable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static jbuild.TestSystemProperties.myClassesJar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class CallHierarchyVisitorTest {

    private static ClassGraph classGraph;
    private static TestVisitor testVisitor;

    @BeforeAll
    static void beforeAll() throws Exception {
        var loader = JarSetPermutations.create(
                new JBuildLog(new PrintStream(new ByteArrayOutputStream()), false));

        var graphs = loader.fromJars(myClassesJar).toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        if (graphs.size() != 1) fail("Expected a single ClassGraph: " + graphs);

        classGraph = graphs.get(0).toClassGraph().toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(classGraph.getTypesByJar().keySet())
                .isEqualTo(Set.of(myClassesJar));
    }

    @AfterAll
    static void afterAll() {
        classGraph = null;
    }

    @BeforeEach
    void setUp() {
        testVisitor = new TestVisitor();
    }

    @AfterEach
    void tearDown() {
        testVisitor = null;
    }

    @Test
    void canVisitEmptyClass() {
        var visitor = new CallHierarchyVisitor(classGraph,
                Set.of(), Set.of(Pattern.compile("Lfoo/EmptyInterface;")));

        visitor.visit(Set.of(myClassesJar), testVisitor);

        assertThat(testVisitor.calls).hasSize(1);

        assertThat(testVisitor.calls.get(0))
                .containsExactly("type", "", "my-tests.jar!foo.EmptyInterface");
    }

    @Test
    void canVisitSimpleClass() {
        var visitor = new CallHierarchyVisitor(classGraph,
                Set.of(), Set.of(Pattern.compile("Lfoo/ExampleLogger;")));

        visitor.visit(Set.of(myClassesJar), testVisitor);

        assertThat(testVisitor.calls).hasSize(5);

        assertThat(testVisitor.calls).containsExactlyInAnyOrder(
                new String[]{"type", "", "my-tests.jar!foo.ExampleLogger"},
                new String[]{"definition", "my-tests.jar!foo.ExampleLogger", "\"<init>\"::(Ljava/io/PrintStream;)V"},
                new String[]{"definition", "my-tests.jar!foo.ExampleLogger", "debug::(Ljava/lang/String;)V"},
                new String[]{"definition", "my-tests.jar!foo.ExampleLogger", "info::(Ljava/lang/String;)V"},
                new String[]{"definition", "my-tests.jar!foo.ExampleLogger", "out::Ljava/io/PrintStream;"}
        );
    }

    @Test
    void canVisitMoreComplexClass() {
        var visitor = new CallHierarchyVisitor(classGraph,
                Set.of(), Set.of(Pattern.compile("Lfoo/Zort;")));

        visitor.visit(Set.of(myClassesJar), testVisitor);

        assertThat(testVisitor.calls).hasSize(8);

        assertThat(testVisitor.calls).containsExactlyInAnyOrder(
                new String[]{"type", "", "my-tests.jar!foo.Zort"},
                new String[]{"definition", "my-tests.jar!foo.Zort", "getBar::(Lfoo/Bar;)Lfoo/Bar;"},
                new String[]{"definition", "my-tests.jar!foo.Zort", "\"<init>\"::()V"},
                new String[]{"definition", "my-tests.jar!foo.Zort", "createBar::()Lfoo/Bar;"},
                new String[]{"code",
                        "my-tests.jar!foo.Zort -> createBar::()Lfoo/Bar;",
                        "Lfoo/Bar;#\"<init>\"::()V"},
                new String[]{"definition", "my-tests.jar!foo.Zort", "static{}::()V"},
                new String[]{"code", "my-tests.jar!foo.Zort -> static{}::()V", "Lfoo/Bar;#\"<init>\"::()V"},
                new String[]{"definition", "my-tests.jar!foo.Zort", "bar::Lfoo/Bar;"}
        );
    }

    private static String chainString(List<Describable> chain) {
        return chain.stream()
                .map(Describable::getDescription)
                .collect(Collectors.joining(" -> "));
    }

    static final class TestVisitor implements CallHierarchyVisitor.Visitor {

        final List<String[]> calls = new ArrayList<>();

        @Override
        public void visit(List<Describable> referenceChain,
                          ClassGraph.TypeDefinitionLocation typeDefinitionLocation) {
            calls.add(new String[]{"type", chainString(referenceChain), typeDefinitionLocation.getDescription()});
        }

        @Override
        public void visit(List<Describable> referenceChain,
                          Definition definition) {
            calls.add(new String[]{"definition", chainString(referenceChain), definition.getDescription()});
        }

        @Override
        public void visit(List<Describable> referenceChain, Code code) {
            calls.add(new String[]{"code", chainString(referenceChain), code.getDescription()});
        }

        @Override
        public void onMissingType(List<Describable> referenceChain,
                                  String typeName) {
            calls.add(new String[]{"missingType", chainString(referenceChain), typeName});
        }

        @Override
        public void onMissingMethod(List<Describable> referenceChain,
                                    ClassGraph.TypeDefinitionLocation typeDefinitionLocation,
                                    Code.Method method) {
            calls.add(new String[]{"missingMethod", chainString(referenceChain),
                    typeDefinitionLocation.getDescription(), method.getDescription()});
        }

        @Override
        public void onMissingField(List<Describable> referenceChain,
                                   ClassGraph.TypeDefinitionLocation typeDefinitionLocation,
                                   Code.Field field) {
            calls.add(new String[]{"missingField", chainString(referenceChain),
                    typeDefinitionLocation.getDescription(), field.getDescription()});
        }

        @Override
        public void onMissingField(List<Describable> referenceChain,
                                   String javaTypeName,
                                   Code.Field field) {
            calls.add(new String[]{"missingField", chainString(referenceChain),
                    javaTypeName, field.getDescription()});
        }
    }

}
