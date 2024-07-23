package jbuild.extension.runner;

import jbuild.api.JBuildLogger;
import jbuild.api.change.ChangeSet;

import java.util.Arrays;
import java.util.List;

public class TestCallable {
    private final JBuildLogger log;
    private final String[] values;

    public TestCallable() {
        this(null);
    }

    public TestCallable(JBuildLogger log) {
        this(log, new String[0]);
    }

    // Selected method to call with args [null, []]: public void CopierTask.run(java.lang.String[])
    public TestCallable(JBuildLogger log, String[] values) {
        this.log = log;
        this.values = values;
    }

    public String hello() {
        return "hello";
    }

    public int add(int a, int b) {
        return a + b;
    }

    public String add(int a, String s) {
        return s + ": " + a;
    }

    public String add(String a, String s) {
        return s + ", " + a;
    }

    public String varargs(double d, String... s) {
        return Arrays.toString(s) + ": " + d;
    }

    public List<Object> getListOfObjects() {
        return List.of("hello", List.of("world", "!"));
    }

    public String run(String[] args) {
        return Arrays.toString(args);
    }

    public String run(String s, Object[] arr, String s2, String s3, ChangeSet[] changes) {
        return s + ':' + Arrays.deepToString(arr) + ':' + s2 + ':' + s3 + ':' + Arrays.deepToString(changes);
    }

    @Override
    public String toString() {
        var values = ", values=" + Arrays.toString(this.values);
        return "TestCallable(log=" + log + values + ")";
    }
}
