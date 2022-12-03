package jbuild.java.tools.runner;

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

    @Override
    public String toString() {
        return "TestCallable";
    }
}
