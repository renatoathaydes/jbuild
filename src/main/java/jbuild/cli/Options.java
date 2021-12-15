package jbuild.cli;

import jbuild.errors.JBuildException;
import jbuild.maven.Scope;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static java.util.Collections.unmodifiableSet;
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

    final Set<String> artifacts;
    final String outDir;

    public FetchOptions(Set<String> artifacts, String outDir) {
        this.artifacts = artifacts;
        this.outDir = outDir;
    }

    static FetchOptions parse(List<String> args) {
        var artifacts = new LinkedHashSet<String>();
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

        return new FetchOptions(unmodifiableSet(artifacts), outDir == null ? "out" : outDir);
    }

}

final class DepsOptions {

    final Set<String> artifacts;
    final EnumSet<Scope> scopes;
    final boolean transitive;
    final boolean optional;

    DepsOptions(Set<String> artifacts,
                EnumSet<Scope> scopes,
                boolean transitive,
                boolean optional) {
        this.artifacts = artifacts;
        this.scopes = scopes;
        this.transitive = transitive;
        this.optional = optional;
    }

    static DepsOptions parse(List<String> args) {
        var artifacts = new LinkedHashSet<String>();
        var scopes = EnumSet.noneOf(Scope.class);
        var transitive = false;
        var optional = false;
        var expectScope = false;

        for (String arg : args) {
            if (expectScope) {
                expectScope = false;
                try {
                    scopes.add(Scope.valueOf(arg.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    throw new JBuildException("invalid scope value: '" + arg +
                            "'. Acceptable values are: " + Arrays.toString(Scope.values()), USER_INPUT);
                }
            } else if (arg.startsWith("-")) {
                if (isEither(arg, "-s", "--scope")) {
                    expectScope = true;
                } else if (isEither(arg, "-O", "--optional")) {
                    optional = true;
                } else if (isEither(arg, "-t", "--transitive")) {
                    transitive = true;
                } else {
                    throw new JBuildException("invalid libs option: " + arg +
                            "\nRun jbuild --help for usage.", USER_INPUT);
                }
            } else {
                artifacts.add(arg);
            }
        }

        return new DepsOptions(unmodifiableSet(artifacts), scopes, transitive, optional);
    }

}

final class VersionsOptions {

    final Set<String> artifacts;

    public VersionsOptions(Set<String> artifacts) {
        this.artifacts = artifacts;
    }

    static VersionsOptions parse(List<String> args) {
        var artifacts = new LinkedHashSet<String>();

        for (String arg : args) {
            if (arg.startsWith("-")) {
                throw new JBuildException("invalid versions option: " + arg +
                        "\nRun jbuild --help for usage.", USER_INPUT);
            } else {
                artifacts.add(arg);
            }
        }

        return new VersionsOptions(unmodifiableSet(artifacts));
    }

}
