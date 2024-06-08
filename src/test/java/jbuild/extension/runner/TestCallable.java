package jbuild.extension.runner;

import jbuild.api.JBuildLogger;

import java.util.Arrays;
import java.util.List;

public class TestCallable {
    private final JBuildLogger log;

    public TestCallable() {
        this(null);
    }

    public TestCallable(JBuildLogger log) {
        this.log = log;
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

    @Override
    public String toString() {
        return "TestCallable(log=" + log + ")";
    }
}
