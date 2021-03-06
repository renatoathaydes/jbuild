package jbuild.log;

import java.io.PrintStream;
import java.util.function.Supplier;

public final class JBuildLog {

    public final PrintStream out;
    private final boolean verbose;
    private volatile boolean enabled = true;

    public JBuildLog(PrintStream out,
                     boolean verbose) {
        this.out = out;
        this.verbose = verbose;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void println(CharSequence message) {
        if (enabled) out.println(message);
    }

    public void print(CharSequence message) {
        if (enabled) out.print(message);
    }

    public void println(Supplier<? extends CharSequence> messageGetter) {
        if (enabled) out.println(messageGetter.get());
    }

    public void print(Supplier<? extends CharSequence> messageGetter) {
        if (enabled) out.print(messageGetter.get());
    }

    public void print(Throwable throwable) {
        throwable.printStackTrace(out);
    }

    public void verbosePrintln(CharSequence message) {
        if (isVerbose()) out.println(message);
    }

    public void verbosePrintln(Supplier<? extends CharSequence> messageGetter) {
        if (isVerbose()) out.println(messageGetter.get());
    }

    public boolean isVerbose() {
        return enabled && verbose;
    }
}
