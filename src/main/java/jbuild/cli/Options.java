package jbuild.cli;

import jbuild.errors.JBuildException;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.unmodifiableList;
import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;
import static jbuild.util.TextUtils.isEither;

final class Options {

    final boolean verbose;
    final boolean help;
    final boolean version;
    final String command;
    final List<String> commandArgs;

    Options(boolean verbose,
            boolean help,
            boolean version,
            String command,
            List<String> commandArgs) {
        this.verbose = verbose;
        this.help = help;
        this.version = version;
        this.command = command;
        this.commandArgs = commandArgs;
    }

    static Options parse(String[] args) {
        boolean verbose = false, help = false, version = false;
        String command = "";
        int i = 0;

        for (i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("-")) {
                command = arg;
                i++;
                break;
            } else if (isEither(arg, "-V", "--verbose")) {
                verbose = true;
            } else if (isEither(arg, "-v", "--version")) {
                version = true;
            } else if (isEither(arg, "-h", "--help")) {
                help = true;
            } else {
                throw new JBuildException("invalid root option: " + arg + "\nRun jbuild --help for usage.", USER_INPUT);
            }
        }

        String[] commandArgs;
        if (i < args.length) {
            commandArgs = new String[args.length - i];
            System.arraycopy(args, i, commandArgs, 0, commandArgs.length);
        } else {
            commandArgs = new String[0];
        }

        return new Options(verbose, help, version, command, List.of(commandArgs));
    }

}

final class FetchOptions {
    final List<String> artifacts;
    final String outDir;

    public FetchOptions(List<String> artifacts, String outDir) {
        this.artifacts = artifacts;
        this.outDir = outDir;
    }

    static FetchOptions parse(List<String> args) {
        var artifacts = new ArrayList<String>();
        String outDir = null;
        var nextIsDir = false;

        for (String arg : args) {
            if (nextIsDir) {
                outDir = arg;
                nextIsDir = false;
            } else if (arg.startsWith("-")) {
                if (isEither(arg, "-d", "--directory")) {
                    if (outDir != null) {
                        throw new JBuildException("fetch option " + arg +
                                " must not appear more than once", USER_INPUT);
                    }
                    nextIsDir = true;
                } else {
                    throw new JBuildException("invalid fetch option: " + arg +
                            "\nRun jbuild --help for usage.", USER_INPUT);
                }
            } else {
                artifacts.add(arg);
            }
        }

        return new FetchOptions(unmodifiableList(artifacts), outDir == null ? "out" : outDir);
    }
}
