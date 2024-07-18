package jbuild.extension.runner;

import jbuild.api.JBuildException;
import jbuild.api.change.ChangeKind;
import jbuild.api.change.ChangeSet;
import jbuild.api.change.FileChange;
import jbuild.log.JBuildLog;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JavaRunnerTest {

    @Test
    void canRunSimpleMethod() {
        var runner = new JavaRunner("", new JBuildLog(System.out, false));

        var result = runner.run(TestCallable.class.getName(), new Object[0], "hello");

        assertThat(result).isEqualTo("hello");
    }

    @Test
    void canRunMethodWithIntArgs() {
        var runner = new JavaRunner("", new JBuildLog(System.out, false));

        var result = runner.run(TestCallable.class.getName(), new Object[0], "add", 1, 2);

        assertThat(result).isEqualTo(3);
    }

    @Test
    void canRunMethodWithStringArrayArg() {
        var runner = new JavaRunner("", new JBuildLog(System.out, false));

        var result = runner.run(TestCallable.class.getName(), new Object[0], "run", List.of("foo", "bar"));

        assertThat(result).isEqualTo("[foo, bar]");
    }

    @Test
    void canRunMethodWithVarArgs() {
        var runner = new JavaRunner("", new JBuildLog(System.out, false));

        var result = runner.run(TestCallable.class.getName(), new Object[0], "varargs", 1.0D);

        assertThat(result).isEqualTo("[]: 1.0");

        var result2 = runner.run(TestCallable.class.getName(), new Object[0], "varargs", 2.0D,
                new String[]{"foo"});

        assertThat(result2).isEqualTo("[foo]: 2.0");

        var result3 = runner.run(TestCallable.class.getName(), new Object[0], "varargs", 3.14D,
                new String[]{"foo", "bar", "zort"});

        assertThat(result3).isEqualTo("[foo, bar, zort]: 3.14");
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
    void canRunMethodTakingChangeSet() {
        var runner = new JavaRunner("", new JBuildLog(System.out, false));

        var changeSets = new ChangeSet[]{
                new ChangeSet(new FileChange[]{
                        new FileChange("foo", ChangeKind.MODIFIED)
                }, new FileChange[]{})
        };

        var result = runner.run(TestCallable.class.getName(), new Object[0], "run",
                "a", new Object[]{"b"}, "c", "d", changeSets);

        assertThat(result).isEqualTo("a:[b]:c:d:" + Arrays.toString(changeSets));
    }

    @Test
    void canCreateClassWithConstructorArg() {
        var log = new JBuildLog(System.out, false);
        var runner = new JavaRunner("", log);

        var result = runner.run(TestCallable.class.getName(), new Object[]{null}, "toString");

        assertThat(result).isEqualTo(new TestCallable(log).toString());

        result = runner.run(TestCallable.class.getName(), new Object[]{log}, "toString");

        assertThat(result).isEqualTo(new TestCallable(log).toString());
    }

    @Test
    void canCreateClassWithArrayConstructorArg() {
        var log = new JBuildLog(System.out, false);
        var runner = new JavaRunner("", log);
        var strings = new String[]{"foo", "bar"};

        var result = runner.run(TestCallable.class.getName(), new Object[]{
                log,
                strings,
        }, "toString");

        assertThat(result).isEqualTo(new TestCallable(log, strings).toString());
    }

    @Test
    void cannotRunNonExistentMethod() {
        var runner = new JavaRunner("", new JBuildLog(System.out, false));

        assertThatThrownBy(() -> runner.run(TestCallable.class.getName(), new Object[0], "notExists"))
                .isInstanceOfAny(JBuildException.class)
                .message()
                .endsWith("No method called 'notExists' found in class jbuild.extension.runner.TestCallable");
    }

    @Test
    void cannotRunMethodIfArgTypeDoesNotMatch() {
        var runner = new JavaRunner("", new JBuildLog(System.out, false));

        assertThatThrownBy(() -> runner.run(TestCallable.class.getName(), new Object[0], "run", "foo"))
                .isInstanceOfAny(JBuildException.class)
                .message()
                .startsWith("Unable to find method that could be invoked with the provided arguments: [foo].");
    }

    @Test
    void cannotRunMethodIfArgTypeDoesNotMatchVarargs() {
        var runner = new JavaRunner("", new JBuildLog(System.out, false));

        assertThatThrownBy(() -> runner.run(TestCallable.class.getName(), new Object[0], "run",
                new Object[]{"foo", 1}))
                .isInstanceOfAny(JBuildException.class)
                .message()
                .startsWith("Unable to find method that could be invoked with the provided arguments: [foo, 1].");
    }

}
