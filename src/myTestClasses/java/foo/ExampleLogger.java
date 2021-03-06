package foo;

import java.io.PrintStream;

public class ExampleLogger {
    public static String getName() {
        return "name";
    }

    private final PrintStream out;

    public ExampleLogger(PrintStream out) {
        this.out = out;
    }

    public void debug(String arg) {
        out.println("DEBUG: " + arg);
    }

    void info(String arg) {
        out.println("INFO: " + arg);
    }
}
