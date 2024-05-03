package jbuild.extension.runner;

import jbuild.log.JBuildLog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaRunnerTest {

    @Test
    void canRunSimpleMethod() {
        var runner = new JavaRunner("", new JBuildLog(System.out, false));

        var result = runner.run(TestCallable.class.getName(), new Object[0], "hello");

        assertThat(result).isEqualTo("hello");
    }

    @Test
    void canRunMethodWithArgs() {
        var runner = new JavaRunner("", new JBuildLog(System.out, false));

        var result = runner.run(TestCallable.class.getName(), new Object[0], "add", 1, 2);

        assertThat(result).isEqualTo(3);
    }

    @Test
    void canRunMethodWithNullAndStringArgs() {
        var runner = new JavaRunner("", new JBuildLog(System.out, false));

        var result = runner.run(TestCallable.class.getName(), new Object[0], "add", "foo", "bar");

        assertThat(result).isEqualTo("bar, foo");

        result = runner.run(TestCallable.class.getName(), new Object[0], "add", "foo", null);

        assertThat(result).isEqualTo("null, foo");

        result = runner.run(TestCallable.class.getName(), new Object[0], "add", null, null);

        assertThat(result).isEqualTo("null, null");

        result = runner.run(TestCallable.class.getName(), new Object[0], "add", 10, null);

        assertThat(result).isEqualTo("null: 10");

        result = runner.run(TestCallable.class.getName(), new Object[0], "add", 10, "foo");

        assertThat(result).isEqualTo("foo: 10");
    }

    @Test
    void canCreateClassWithConstructorArg() {
        var log = new JBuildLog(System.out, false);
        var runner = new JavaRunner("", log);

        var result = runner.run(TestCallable.class.getName(), new Object[]{null}, "toString");

        assertThat(result).isEqualTo(new TestCallable(log).toString());
    }

}
