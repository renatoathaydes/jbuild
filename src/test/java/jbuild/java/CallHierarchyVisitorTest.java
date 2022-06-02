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
import java.io.File;
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
                Set.of(), Set.of(Pattern.compile("foo.EmptyInterface")));

        visitor.visit(Set.of(myClassesJar), testVisitor);

        assertThat(testVisitor.calls).hasSize(1);

        assertThat(testVisitor.calls.get(0))
                .containsExactly("type", "", "my-tests.jar!foo.EmptyInterface");
    }

    @Test
    void canVisitSimpleClass() {
        var visitor = new CallHierarchyVisitor(classGraph,
                Set.of(), Set.of(Pattern.compile("foo.ExampleLogger")));

        visitor.visit(Set.of(myClassesJar), testVisitor);

        assertThat(testVisitor.calls).hasSize(5);

        assertThat(testVisitor.calls).containsExactlyInAnyOrder(
                new String[]{"type", "", "my-tests.jar!foo.ExampleLogger"},
                new String[]{"definition", "my-tests.jar!foo.ExampleLogger", "\"<init>\"(java.io.PrintStream)::void"},
                new String[]{"definition", "my-tests.jar!foo.ExampleLogger", "debug(java.lang.String)::void"},
                new String[]{"definition", "my-tests.jar!foo.ExampleLogger", "info(java.lang.String)::void"},
                new String[]{"definition", "my-tests.jar!foo.ExampleLogger", "out::java.io.PrintStream"}
        );
    }

    @Test
    void canVisitRecursiveClass() {
        var visitor = new CallHierarchyVisitor(classGraph,
                Set.of(), Set.of(Pattern.compile("recursion.Recursive")));

        visitor.visit(Set.of(myClassesJar), testVisitor);

        assertThat(testVisitor.calls).hasSize(3);

        assertThat(testVisitor.calls).containsExactlyInAnyOrder(
                new String[]{"type", "", "my-tests.jar!recursion.Recursive"},
                new String[]{"definition", "my-tests.jar!recursion.Recursive", "\"<init>\"()::void"},
                new String[]{"definition", "my-tests.jar!recursion.Recursive", "factorial(int)::int"}
        );
    }

    @Test
    void canVisitInterRecursiveClasses() {
        var visitor = new CallHierarchyVisitor(classGraph,
                Set.of(), Set.of(Pattern.compile("recursion.P[i|o]ng")));

        visitor.visit(Set.of(myClassesJar), testVisitor);

        assertThat(testVisitor.calls).containsExactlyInAnyOrder(
                new String[]{"type", "", "my-tests.jar!recursion.Ping"},
                new String[]{"type", "", "my-tests.jar!recursion.Pong"},
                new String[]{"definition", "my-tests.jar!recursion.Ping", "\"<init>\"()::void"},
                new String[]{"definition", "my-tests.jar!recursion.Ping", "ping(recursion.Pong)::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.Ping -> ping(recursion.Pong)::void",
                        "recursion.Pong#pong(recursion.Ping)::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.Ping -> ping(recursion.Pong)::void " +
                                "-> recursion.Pong#pong(recursion.Ping)::void",
                        "recursion.Ping#ping(recursion.Pong)::void"},
                new String[]{"definition", "my-tests.jar!recursion.Pong", "\"<init>\"()::void"},
                new String[]{"definition", "my-tests.jar!recursion.Pong", "pong(recursion.Ping)::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.Pong -> pong(recursion.Ping)::void",
                        "recursion.Ping#ping(recursion.Pong)::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.Pong -> pong(recursion.Ping)::void " +
                                "-> recursion.Ping#ping(recursion.Pong)::void",
                        "recursion.Pong#pong(recursion.Ping)::void"}
        );
    }

    @Test
    void canVisitInterRecursiveClassesThirdDegree() {
        var visitor = new CallHierarchyVisitor(classGraph, Set.of(),
                Set.of(Pattern.compile("recursion\\.TicTacToe.*")));

        visitor.visit(Set.of(myClassesJar), testVisitor);

        assertThat(testVisitor.calls).containsExactlyInAnyOrder(
                new String[]{"type", "", "my-tests.jar!recursion.TicTacToe"},
                new String[]{"type", "", "my-tests.jar!recursion.TicTacToe$Tic"},
                new String[]{"type", "", "my-tests.jar!recursion.TicTacToe$Tac"},
                new String[]{"type", "", "my-tests.jar!recursion.TicTacToe$Toe"},
                new String[]{"definition", "my-tests.jar!recursion.TicTacToe$Tac", "\"<init>\"()::void"},
                new String[]{"definition", "my-tests.jar!recursion.TicTacToe$Tac", "tac()::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.TicTacToe$Tac -> tac()::void",
                        "recursion.TicTacToe$Toe#\"<init>\"()::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.TicTacToe$Tac -> tac()::void",
                        "recursion.TicTacToe$Toe#toe()::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.TicTacToe$Tac -> tac()::void -> recursion.TicTacToe$Toe#toe()::void",
                        "recursion.TicTacToe$Tic#\"<init>\"()::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.TicTacToe$Tac -> tac()::void -> recursion.TicTacToe$Toe#toe()::void",
                        "recursion.TicTacToe$Tic#tic()::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.TicTacToe$Tac -> tac()::void -> recursion.TicTacToe$Toe#toe()::void -> recursion.TicTacToe$Tic#tic()::void",
                        "recursion.TicTacToe$Tac#\"<init>\"()::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.TicTacToe$Tac -> tac()::void -> recursion.TicTacToe$Toe#toe()::void -> recursion.TicTacToe$Tic#tic()::void",
                        "recursion.TicTacToe$Tac#tac()::void"},
                new String[]{"definition", "my-tests.jar!recursion.TicTacToe", "\"<init>\"()::void"},
                new String[]{"definition", "my-tests.jar!recursion.TicTacToe", "go()::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.TicTacToe -> go()::void",
                        "recursion.TicTacToe$Tic#\"<init>\"()::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.TicTacToe -> go()::void",
                        "recursion.TicTacToe$Tic#tic()::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.TicTacToe -> go()::void -> recursion.TicTacToe$Tic#tic()::void",
                        "recursion.TicTacToe$Tac#\"<init>\"()::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.TicTacToe -> go()::void -> recursion.TicTacToe$Tic#tic()::void",
                        "recursion.TicTacToe$Tac#tac()::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.TicTacToe -> go()::void -> recursion.TicTacToe$Tic#tic()::void -> recursion.TicTacToe$Tac#tac()::void",
                        "recursion.TicTacToe$Toe#\"<init>\"()::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.TicTacToe -> go()::void -> recursion.TicTacToe$Tic#tic()::void -> recursion.TicTacToe$Tac#tac()::void",
                        "recursion.TicTacToe$Toe#toe()::void"},
                new String[]{"definition", "my-tests.jar!recursion.TicTacToe$Tic", "tic()::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.TicTacToe$Tic -> tic()::void",
                        "recursion.TicTacToe$Tac#\"<init>\"()::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.TicTacToe$Tic -> tic()::void",
                        "recursion.TicTacToe$Tac#tac()::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.TicTacToe$Tic -> tic()::void -> recursion.TicTacToe$Tac#tac()::void",
                        "recursion.TicTacToe$Toe#\"<init>\"()::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.TicTacToe$Tic -> tic()::void -> recursion.TicTacToe$Tac#tac()::void",
                        "recursion.TicTacToe$Toe#toe()::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.TicTacToe$Tic -> tic()::void -> recursion.TicTacToe$Tac#tac()::void -> recursion.TicTacToe$Toe#toe()::void",
                        "recursion.TicTacToe$Tic#\"<init>\"()::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.TicTacToe$Tic -> tic()::void -> recursion.TicTacToe$Tac#tac()::void -> recursion.TicTacToe$Toe#toe()::void",
                        "recursion.TicTacToe$Tic#tic()::void"},
                new String[]{"definition", "my-tests.jar!recursion.TicTacToe$Tic", "\"<init>\"()::void"},
                new String[]{"definition", "my-tests.jar!recursion.TicTacToe$Toe", "\"<init>\"()::void"},
                new String[]{"definition", "my-tests.jar!recursion.TicTacToe$Toe", "toe()::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.TicTacToe$Toe -> toe()::void",
                        "recursion.TicTacToe$Tic#\"<init>\"()::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.TicTacToe$Toe -> toe()::void",
                        "recursion.TicTacToe$Tic#tic()::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.TicTacToe$Toe -> toe()::void -> recursion.TicTacToe$Tic#tic()::void",
                        "recursion.TicTacToe$Tac#\"<init>\"()::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.TicTacToe$Toe -> toe()::void -> recursion.TicTacToe$Tic#tic()::void",
                        "recursion.TicTacToe$Tac#tac()::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.TicTacToe$Toe -> toe()::void -> recursion.TicTacToe$Tic#tic()::void -> recursion.TicTacToe$Tac#tac()::void",
                        "recursion.TicTacToe$Toe#\"<init>\"()::void"},
                new String[]{"code",
                        "my-tests.jar!recursion.TicTacToe$Toe -> toe()::void -> recursion.TicTacToe$Tic#tic()::void -> recursion.TicTacToe$Tac#tac()::void",
                        "recursion.TicTacToe$Toe#toe()::void"}
        );
    }

    @Test
    void canVisitMoreComplexClass() {
        var visitor = new CallHierarchyVisitor(classGraph,
                Set.of(), Set.of(Pattern.compile("foo.Zort")));

        visitor.visit(Set.of(myClassesJar), testVisitor);

        assertThat(testVisitor.calls).containsExactlyInAnyOrder(
                new String[]{"type", "", "my-tests.jar!foo.Zort"},
                new String[]{"definition", "my-tests.jar!foo.Zort", "getBar(foo.Bar)::foo.Bar"},
                new String[]{"definition", "my-tests.jar!foo.Zort", "\"<init>\"()::void"},
                new String[]{"definition", "my-tests.jar!foo.Zort", "createBar()::foo.Bar"},
                new String[]{"code",
                        "my-tests.jar!foo.Zort -> createBar()::foo.Bar",
                        "foo.Bar#\"<init>\"()::void"},
                new String[]{"definition", "my-tests.jar!foo.Zort", "static{}()::void"},
                new String[]{"code", "my-tests.jar!foo.Zort -> static{}()::void", "foo.Bar#\"<init>\"()::void"},
                new String[]{"definition", "my-tests.jar!foo.Zort", "bar::foo.Bar"}
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
        public void startJar(File jar) {
        }

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
