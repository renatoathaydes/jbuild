package jbuild.commands;

import jbuild.TestSystemProperties;
import jbuild.log.JBuildLog;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

public class RequirementsCommandTest {

    @Test
    void canCheckJarRequirementsEmpty() throws ExecutionException, InterruptedException, TimeoutException {
        var bytesOut = new ByteArrayOutputStream();
        var out = new PrintStream(bytesOut);

        var log = new JBuildLog(out, false);
        var visitor = new TestJarVisitor();
        var command = new RequirementsCommandExecutor(log, visitor);

        var result = command.execute(Set.of(TestSystemProperties.myClassesJar.getPath()), false);
        result.toCompletableFuture().get(5, TimeUnit.MINUTES);

        assertThat(visitor.jars).containsExactly(TestSystemProperties.myClassesJar.getName());
        assertThat(visitor.types).isEmpty();
        assertThat(visitor.done.get()).isTrue();
    }

    @Test
    void canCheckJarRequirements() throws ExecutionException, InterruptedException, TimeoutException {
        var bytesOut = new ByteArrayOutputStream();
        var out = new PrintStream(bytesOut);

        var log = new JBuildLog(out, false);
        var visitor = new TestJarVisitor();
        var command = new RequirementsCommandExecutor(log, visitor);

        var result = command.execute(Set.of(TestSystemProperties.otherClassesJar.getPath()), false);
        result.toCompletableFuture().get(5, TimeUnit.MINUTES);

        assertThat(visitor.jars).containsExactly(TestSystemProperties.otherClassesJar.getName());
        assertThat(visitor.types).containsOnly(
                "Lfoo/FunctionalCode;",
                "Lfoo/Something;",
                "Lfoo/MultiInterface;",
                "Lfoo/SomeEnum;",
                "Lfoo/SomethingSpecific;",
                "Lfoo/ExampleLogger;",
                "Lgenerics/Generics;",
                "Lfoo/EmptyInterface;",
                "Lfoo/Bar;",
                "Lfoo/Zort;",
                "Lgenerics/BaseA;",
                "Lgenerics/Base;",
                "Lgenerics/ComplexType;",
                "Lfoo/Fields;"
        );
        assertThat(visitor.done.get()).isTrue();
    }

    @Test
    void canCheckPerClassRequirements() throws ExecutionException, InterruptedException, TimeoutException {
        var bytesOut = new ByteArrayOutputStream();
        var out = new PrintStream(bytesOut);

        var log = new JBuildLog(out, false);
        var visitor = new TestPerClassVisitor();
        var command = new RequirementsCommandExecutor(log, visitor);

        var result = command.execute(Set.of(TestSystemProperties.otherClassesJar.getPath()), true);
        result.toCompletableFuture().get(5, TimeUnit.MINUTES);

        assertThat(visitor.jars).containsExactly(TestSystemProperties.otherClassesJar.getName());
        assertThat(visitor.types).containsOnlyKeys("other/CallsSuperMethod", "other/CallsZortToCreateBar",
                "other/ExtendsBar", "other/HasSomething", "other/ImplementsEmptyInterface", "other/ReadsFieldOfZort",
                "other/UsesArrayOfFunctionalCode", "other/UsesBar", "other/UsesBaseA", "other/UsesBaseViaGenerics",
                "other/UsesComplexType", "other/UsesEnum", "other/UsesFields", "other/UsesGenerics",
                "other/UsesMethodHandleFromExampleLogger", "other/UsesMultiInterface", "other/UsesComplexType$Param",
                "other/UsesEnum$1");
        assertThat(visitor.done.get()).isTrue();

        assertThat(visitor.types.get("other/CallsSuperMethod").requirements).containsExactly("Lfoo/Something;",
                "Lfoo/SomethingSpecific;");

        assertThat(visitor.types.get("other/CallsZortToCreateBar").requirements).containsExactly("Lfoo/Bar;",
                "Lfoo/Zort;");

        assertThat(visitor.types.get("other/ExtendsBar").requirements).containsExactly("Lfoo/Bar;");

        assertThat(visitor.types.get("other/HasSomething").requirements).containsExactly("Lfoo/Something;");

        assertThat(visitor.types.get("other/ImplementsEmptyInterface").requirements)
                .containsExactly("Lfoo/EmptyInterface;");

        assertThat(visitor.types.get("other/ReadsFieldOfZort").requirements).containsExactly("Lfoo/Bar;", "Lfoo/Zort;");

        assertThat(visitor.types.get("other/UsesArrayOfFunctionalCode").requirements)
                .containsExactly("Lfoo/FunctionalCode;");

        assertThat(visitor.types.get("other/UsesBar").requirements).containsExactly("Lfoo/Bar;");

        assertThat(visitor.types.get("other/UsesBaseA").requirements).containsExactly("Lgenerics/BaseA;");

        assertThat(visitor.types.get("other/UsesBaseViaGenerics").requirements).containsExactly("Lgenerics/Base;");

        assertThat(visitor.types.get("other/UsesComplexType").requirements).containsExactly(
                "Lfoo/Zort;", "Lgenerics/ComplexType;", "Lother/UsesComplexType$Param;");

        assertThat(visitor.types.get("other/UsesEnum").requirements).containsExactly(
                "Lfoo/SomeEnum;", "Lother/UsesEnum$1;");

        assertThat(visitor.types.get("other/UsesFields").requirements).containsExactly("Lfoo/Fields;");

        assertThat(visitor.types.get("other/UsesGenerics").requirements).containsExactly(
                "Lgenerics/Base;", "Lgenerics/BaseA;", "Lgenerics/Generics;");

        assertThat(visitor.types.get("other/UsesMethodHandleFromExampleLogger").requirements)
                .containsExactly("Lfoo/ExampleLogger;");

        assertThat(visitor.types.get("other/UsesMultiInterface").requirements).containsExactly("Lfoo/MultiInterface;");

        assertThat(visitor.types.get("other/UsesComplexType$Param").requirements)
                .containsExactly("Lfoo/EmptyInterface;", "Lgenerics/Generics;", "Lother/UsesComplexType;");

        assertThat(visitor.types.get("other/UsesEnum$1").requirements)
                .containsExactly("Lfoo/SomeEnum;", "Lother/UsesEnum;");
    }

    private static class TestJarVisitor implements RequirementsCommandExecutor.TypeVisitor {
        final Deque<String> jars = new LinkedBlockingDeque<>(64);
        final Deque<String> types = new LinkedBlockingDeque<>(64);
        final AtomicBoolean done = new AtomicBoolean();

        @Override
        public void startJar(File jar) {
            jars.offer(jar.getName());
        }

        @Override
        public void handleTypeRequirements(String type, RequirementsCommandExecutor.TypeRequirements typeRequirements) {
            throw new IllegalStateException("did not expect per requirements per type");
        }

        @Override
        public void handleJarRequirements(TreeSet<String> types) {
            this.types.addAll(types);
        }

        @Override
        public void onDone() {
            done.set(true);
        }
    }

    private static class TestPerClassVisitor implements RequirementsCommandExecutor.TypeVisitor {
        final Deque<String> jars = new LinkedBlockingDeque<>(64);
        final Map<String, RequirementsCommandExecutor.TypeRequirements> types = new HashMap<>(24);
        final AtomicBoolean done = new AtomicBoolean();

        @Override
        public void startJar(File jar) {
            jars.offer(jar.getName());
        }

        @Override
        public void handleTypeRequirements(String type, RequirementsCommandExecutor.TypeRequirements typeRequirements) {
            var oldType = types.put(type, typeRequirements);
            if (oldType != null) {
                throw new IllegalStateException("Replacing type: " + type);
            }
        }

        @Override
        public void handleJarRequirements(TreeSet<String> types) {
            throw new IllegalStateException("did not expect per requirements per type");
        }

        @Override
        public void onDone() {
            done.set(true);
        }
    }
}
