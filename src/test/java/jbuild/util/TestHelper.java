package jbuild.util;

import jbuild.log.JBuildLog;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;

public final class TestHelper {
    public static Map.Entry<JBuildLog, ByteArrayOutputStream> createLog(boolean verbose) {
        var stream = new ByteArrayOutputStream(512);
        var printer = new PrintStream(stream);
        return Map.entry(new JBuildLog(printer, verbose), stream);
    }
}
