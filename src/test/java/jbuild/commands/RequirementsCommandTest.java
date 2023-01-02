package jbuild.commands;

import jbuild.TestSystemProperties;
import jbuild.log.JBuildLog;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Deque;
import java.util.Set;
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
        var visitor = new TestVisitor();
        var command = new RequirementsCommandExecutor(log, visitor);

        var result = command.execute(Set.of(TestSystemProperties.myClassesJar.getPath()));
        result.toCompletableFuture().get(5, TimeUnit.MINUTES);

        assertThat(visitor.jars).containsExactly(TestSystemProperties.myClassesJar.getName());
        assertThat(visitor.missingTypes).isEmpty();
        assertThat(visitor.done.get()).isTrue();
    }

    @Test
    void canCheckJarRequirements() throws ExecutionException, InterruptedException, TimeoutException {
        var bytesOut = new ByteArrayOutputStream();
        var out = new PrintStream(bytesOut);

        var log = new JBuildLog(out, false);
        var visitor = new TestVisitor();
        var command = new RequirementsCommandExecutor(log, visitor);

        var result = command.execute(Set.of(TestSystemProperties.otherClassesJar.getPath()));
        result.toCompletableFuture().get(5, TimeUnit.MINUTES);

        assertThat(visitor.jars).containsExactly(TestSystemProperties.otherClassesJar.getName());
        assertThat(visitor.missingTypes).containsOnly(
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

    private static class TestVisitor implements RequirementsCommandExecutor.MissingTypeVisitor {
        final Deque<String> jars = new LinkedBlockingDeque<>(64);
        final Deque<String> missingTypes = new LinkedBlockingDeque<>(64);
        final AtomicBoolean done = new AtomicBoolean();

        @Override
        public void startJar(File jar) {
            jars.offer(jar.getName());
        }

        @Override
        public void onMissingType(String typeName) {
            missingTypes.offer(typeName);
        }

        @Override
        public void onDone() {
            done.set(true);
        }
    }
}
