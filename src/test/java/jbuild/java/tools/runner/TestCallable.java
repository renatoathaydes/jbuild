package jbuild.java.tools.runner;

import java.util.Arrays;

public class TestCallable {
    String hello() {
        return "hello";
    }

    int add(int a, int b) {
        return a + b;
    }

    String add(int a, String s) {
        return s + ": " + a;
    }

    String varargs(double d, String... s) {
        return Arrays.toString(s) + ": " + d;
    }

    void run(String[] args) {
    }

    @Override
    public String toString() {
        return "TestCallable";
    }
}
