package jbuild.cli;

import jbuild.errors.JBuildException;

import java.util.ArrayList;
import java.util.List;

import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;

final class Options {

    final List<String> artifacts = new ArrayList<>();
    String outDir = "out";
    boolean verbose;
    boolean help;
    boolean version;

    Options parse(String[] args) {
        var nextIsDir = false;
        for (String arg : args) {
            if (nextIsDir) {
                outDir = arg;
                nextIsDir = false;
            } else if (!arg.startsWith("-")) {
                artifacts.add(arg);
            } else if (isEither(arg, "-V", "--verbose")) {
                verbose = true;
            } else if (isEither(arg, "-v", "--version")) {
                version = true;
                return this;
            } else if (isEither(arg, "-h", "--help")) {
                help = true;
                return this;
            } else if (isEither(arg, "-d", "--directory")) {
                nextIsDir = true;
            } else {
                throw new JBuildException("Invalid option: " + arg + "\nRun jbuild --help for usage.", USER_INPUT);
            }
        }
        return this;
    }

    private static boolean isEither(String arg, String opt1, String opt2) {
        return opt1.equals(arg) || opt2.equals(arg);
    }
}
