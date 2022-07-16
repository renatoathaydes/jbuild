package foo;

import java.nio.charset.StandardCharsets;

public class Something {
    public String some() {
        return "something";
    }

    public int varargs(String... foo) {
        // make sure array clone method is found
        foo.clone();
        return foo.length + foo(foo[0].getBytes(StandardCharsets.UTF_8));
    }

    private int foo(byte[] bytes) {
        // primitive array
        bytes.clone();
        return bytes.length;
    }
}
