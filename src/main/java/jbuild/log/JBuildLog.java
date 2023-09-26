package jbuild.log;

import java.io.PrintStream;
import java.util.function.Supplier;

public final class JBuildLog {

    public final PrintStream out;
    private final boolean verbose;
    private final String prefix;
    private volatile boolean enabled = true;

    public JBuildLog(PrintStream out,
                     boolean verbose) {
        this(out, verbose, null);
    }

    public JBuildLog(PrintStream out,
                     boolean verbose,
                     String prefix) {
        this.out = out;
        this.verbose = verbose;
        this.prefix = prefix;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void println(CharSequence message) {
        if (enabled) doPrintln(message);
    }

    public void print(CharSequence message) {
        if (enabled) doPrint(message);
    }

    public void println(Supplier<? extends CharSequence> messageGetter) {
        if (enabled) doPrintln(messageGetter.get());
    }

    public void print(Supplier<? extends CharSequence> messageGetter) {
        if (enabled) doPrint(messageGetter.get());
    }

    public void print(Throwable throwable) {
        if (prefix == null) {
            throwable.printStackTrace(out);
        } else {
            out.println(prefix + ' ' + throwable);
            for (var traceElement : throwable.getStackTrace()) {
                doPrintln(prefix + " \tat " + traceElement);
            }
        }
    }

    public void verbosePrintln(CharSequence message) {
        if (isVerbose()) doPrintln(message);
    }

    public void verbosePrintln(Supplier<? extends CharSequence> messageGetter) {
        if (isVerbose()) doPrintln(messageGetter.get());
    }

    public boolean isVerbose() {
        return enabled && verbose;
    }

    private void doPrintln(CharSequence message) {
        if (prefix != null) {
            out.print(prefix);
            out.print(' ');
        }
        out.println(message);
    }

    private void doPrint(CharSequence message) {
        if (prefix != null) {
            out.print(prefix);
            out.print(' ');
        }
        out.print(message);
    }
}
