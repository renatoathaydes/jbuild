package jbuild.log;

import jbuild.api.JBuildLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.function.Supplier;

public final class JBuildLog implements JBuildLogger {

    public final PrintStream out;
    private final boolean verbose;
    private final String prefix;
    private volatile boolean enabled = true;
    private volatile boolean lastCharWasNewLine = true;

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

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void println(CharSequence message) {
        if (enabled) doPrintln(message);
    }

    @Override
    public void print(CharSequence message) {
        if (enabled) doPrint(message);
    }

    @Override
    public void println(Supplier<? extends CharSequence> messageGetter) {
        if (enabled) doPrintln(messageGetter.get());
    }

    @Override
    public void print(Supplier<? extends CharSequence> messageGetter) {
        if (enabled) doPrint(messageGetter.get());
    }

    @Override
    public void print(Throwable throwable) {
        if (prefix == null) {
            throwable.printStackTrace(out);
        } else {
            out.println(prefix + ' ' + throwable);
            for (var traceElement : throwable.getStackTrace()) {
                doPrintln(prefix + " \tat " + traceElement);
            }
            lastCharWasNewLine = true;
        }
    }

    @Override
    public void verbosePrintln(CharSequence message) {
        if (isVerbose()) doPrintln(message);
    }

    @Override
    public void verbosePrintln(Supplier<? extends CharSequence> messageGetter) {
        if (isVerbose()) doPrintln(messageGetter.get());
    }

    @Override
    public boolean isVerbose() {
        return enabled && verbose;
    }

    private void print(byte[] buffer, int len) {
        if (isEnabled()) {
            out.print(prefix);
            out.print(' ');
            out.write(buffer, 0, len);
            lastCharWasNewLine = ((char) buffer[len - 1]) == '\n';
        }
    }

    private void doPrintln(CharSequence message) {
        if (prefix != null && lastCharWasNewLine) {
            out.print(prefix);
            out.print(' ');
        }
        out.println(message);
        lastCharWasNewLine = true;
    }

    private void doPrint(CharSequence message) {
        if (prefix != null && lastCharWasNewLine) {
            out.print(prefix);
            out.print(' ');
        }
        out.print(message);
        lastCharWasNewLine = message.charAt(message.length() - 1) == '\n';
    }

    public PrintStream getPrintStream() {
        return new PrintStream(new LogOutputStream(this), true);
    }

    private static final class LogOutputStream extends OutputStream {
        private final JBuildLog log;
        private final byte[] buffer = new byte[1024];
        private int index;

        public LogOutputStream(JBuildLog log) {
            this.log = log;
        }

        @Override
        public void write(int b) {
            if (index < buffer.length) {
                buffer[index++] = (byte) b;
            }
        }

        @Override
        public void flush() throws IOException {
            super.flush();
            log.print(buffer, index);
            index = 0;
        }
    }

}
