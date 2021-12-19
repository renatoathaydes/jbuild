package jbuild.cli;

import jbuild.artifact.ArtifactRetriever;
import jbuild.artifact.file.FileArtifactRetriever;
import jbuild.artifact.http.HttpArtifactRetriever;
import jbuild.errors.ArtifactRetrievalError;
import jbuild.errors.JBuildException;
import jbuild.maven.Scope;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.toList;
import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;
import static jbuild.util.TextUtils.isEither;

final class Options {

    final boolean verbose;
    final boolean help;
    final boolean version;
    final String command;
    final List<String> repositories;
    final List<String> commandArgs;

    Options(boolean verbose,
            boolean help,
            boolean version,
            String command,
            List<String> repositories,
            List<String> commandArgs) {
        this.verbose = verbose;
        this.help = help;
        this.version = version;
        this.command = command;
        this.repositories = repositories;
        this.commandArgs = commandArgs;
    }

    List<ArtifactRetriever<? extends ArtifactRetrievalError>> getRetrievers() {
        return repositories.stream()
                .map(address -> {
                    if (address.startsWith("http://") || address.startsWith("https://")) {
                        return new HttpArtifactRetriever(address);
                    }
                    return new FileArtifactRetriever(Paths.get(address));
                }).collect(toList());
    }

    static Options parse(String[] args) {
        boolean verbose = false, help = false, version = false;
        String command = "";
        var repositories = new ArrayList<String>(4);

        var expectingRepository = false;
        int i;

        for (i = 0; i < args.length; i++) {
            String arg = args[i];
            if (expectingRepository) {
                expectingRepository = false;
                repositories.add(arg);
            } else if (!arg.startsWith("-")) {
                command = arg;
                i++;
                break;
            } else if (isEither(arg, "-r", "--repository")) {
                expectingRepository = true;
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

        if (expectingRepository) {
            throw new JBuildException("expecting value for 'repository' option", USER_INPUT);
        }

        String[] commandArgs;
        if (i < args.length) {
            commandArgs = new String[args.length - i];
            System.arraycopy(args, i, commandArgs, 0, commandArgs.length);
        } else {
            commandArgs = new String[0];
        }

        return new Options(verbose, help, version, command, repositories, List.of(commandArgs));
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
    final boolean licenses;

    DepsOptions(Set<String> artifacts,
                EnumSet<Scope> scopes,
                boolean transitive,
                boolean optional,
                boolean licenses) {
        this.artifacts = artifacts;
        this.scopes = scopes;
        this.transitive = transitive;
        this.optional = optional;
        this.licenses = licenses;
    }

    static DepsOptions parse(List<String> args) {
        var artifacts = new LinkedHashSet<String>();
        var scopes = EnumSet.noneOf(Scope.class);
        boolean transitive = false, optional = false, licenses = false, expectScope = false;

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
                } else if (isEither(arg, "-l", "--licenses")) {
                    licenses = true;
                } else {
                    throw new JBuildException("invalid libs option: " + arg +
                            "\nRun jbuild --help for usage.", USER_INPUT);
                }
            } else {
                artifacts.add(arg);
            }
        }

        if (expectScope) {
            throw new JBuildException("expecting value for 'scope' option", USER_INPUT);
        }

        // if no scopes are included explicitly, use all
        if (scopes.isEmpty()) scopes = EnumSet.allOf(Scope.class);

        return new DepsOptions(unmodifiableSet(artifacts), scopes, transitive, optional, licenses);
    }

}

final class InstallOptions {

    final Set<String> artifacts;
    final EnumSet<Scope> scopes;
    final String outDir;
    final String repoDir;
    final boolean optional;

    InstallOptions(Set<String> artifacts,
                   EnumSet<Scope> scopes,
                   String outDir,
                   String repoDir,
                   boolean optional) {
        this.artifacts = artifacts;
        this.scopes = scopes;
        this.outDir = outDir;
        this.repoDir = repoDir;
        this.optional = optional;
    }

    static InstallOptions parse(List<String> args) {
        var artifacts = new LinkedHashSet<String>();
        var scopes = EnumSet.noneOf(Scope.class);
        var optional = false;
        String outDir = null, repoDir = null;
        var expectScope = false;
        var expectOutDir = false;
        var expectRepoDir = false;

        for (String arg : args) {
            if (expectScope) {
                expectScope = false;
                try {
                    scopes.add(Scope.valueOf(arg.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    throw new JBuildException("invalid scope value: '" + arg +
                            "'. Acceptable values are: " + Arrays.toString(Scope.values()), USER_INPUT);
                }
            } else if (expectOutDir) {
                expectOutDir = false;
                if (outDir == null) {
                    outDir = arg;
                } else {
                    throw new JBuildException("cannot provide output directory more than once" +
                            "\nRun jbuild --help for usage.", USER_INPUT);
                }
            } else if (expectRepoDir) {
                expectRepoDir = false;
                if (repoDir == null) {
                    repoDir = arg;
                } else {
                    throw new JBuildException("cannot provide repository directory more than once" +
                            "\nRun jbuild --help for usage.", USER_INPUT);
                }
            } else if (arg.startsWith("-")) {
                if (isEither(arg, "-s", "--scope")) {
                    expectScope = true;
                } else if (isEither(arg, "-O", "--optional")) {
                    optional = true;
                } else if (isEither(arg, "-d", "--directory")) {
                    expectOutDir = true;
                } else if (isEither(arg, "-r", "--repository")) {
                    expectRepoDir = true;
                } else {
                    throw new JBuildException("invalid libs option: " + arg +
                            "\nRun jbuild --help for usage.", USER_INPUT);
                }
            } else {
                artifacts.add(arg);
            }
        }

        if (expectScope || expectOutDir || expectRepoDir) {
            var opt = expectScope ? "scope" : expectOutDir ? "directory" : "repository";
            throw new JBuildException("expecting value for '" + opt + "' option", USER_INPUT);
        }

        // if no scopes are included explicitly, use runtime
        if (scopes.isEmpty()) scopes = EnumSet.of(Scope.RUNTIME);

        if (outDir == null && repoDir == null) outDir = "out";
        if (outDir != null && repoDir != null) {
            throw new JBuildException("cannot specify both 'directory' and 'repository' options together." +
                    "\nRun jbuild --help for usage.", USER_INPUT);
        }

        return new InstallOptions(unmodifiableSet(artifacts), scopes, outDir, repoDir, optional);
    }

}

final class FixOptions {

    final String inputDir;
    final boolean interactive;

    public FixOptions(String inputDir, boolean interactive) {
        this.inputDir = inputDir;
        this.interactive = interactive;
    }

    static FixOptions parse(List<String> args) {
        String inputDir = null;
        var interactive = true;

        for (var arg : args) {
            if (arg.startsWith("-")) {
                if (isEither(arg, "-y", "--yes")) {
                    interactive = false;
                } else {
                    throw new JBuildException("invalid fix option: " + arg +
                            "\nRun jbuild --help for usage.", USER_INPUT);
                }
            } else {
                if (inputDir != null) {
                    throw new JBuildException("cannot provide more than one input directory for fix command" +
                            "\nRun jbuild --help for usage.", USER_INPUT);
                }
                inputDir = arg;
            }
        }

        return new FixOptions(inputDir, interactive);
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
