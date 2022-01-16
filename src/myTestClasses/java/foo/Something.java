package foo;

public class Something {
    public String some() {
        return "something";
    }

    public int varargs(String... foo) {
        return foo.length;
    }
}
