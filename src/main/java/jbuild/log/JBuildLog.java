package jbuild.log;

import java.io.PrintStream;
import java.util.function.Supplier;

public final class JBuildLog {

    private final PrintStream out;
    private final boolean verbose;
    private boolean enabled = true;

    public JBuildLog(PrintStream out,
                     boolean verbose) {
        this.out = out;
        this.verbose = verbose;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void println(String message) {
        if (enabled) out.println(message);
    }

    public void print(String message) {
        if (enabled) out.print(message);
    }

    public void println(Supplier<String> messageGetter) {
        if (enabled) out.println(messageGetter.get());
    }

    public void print(Supplier<String> messageGetter) {
        if (enabled) out.print(messageGetter.get());
    }

    public void verbosePrintln(String message) {
        if (verbose && enabled) out.println(message);
    }

    public void verbosePrintln(Supplier<String> messageGetter) {
        if (verbose && enabled) out.println(messageGetter.get());
    }

    public boolean isVerbose() {
        return verbose;
    }
}
