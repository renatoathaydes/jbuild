package jbuild.cli;

import jbuild.artifact.ArtifactRetriever;
import jbuild.artifact.file.FileArtifactRetriever;
import jbuild.artifact.http.HttpArtifactRetriever;
import jbuild.commands.InstallCommandExecutor;
import jbuild.errors.ArtifactRetrievalError;
import jbuild.errors.JBuildException;
import jbuild.maven.Scope;
import jbuild.util.Either;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.toList;
import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;
import static jbuild.util.TextUtils.LINE_END;
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
                throw new JBuildException("invalid root option: " + arg + LINE_END + "Run jbuild --help for usage.", USER_INPUT);
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
                            LINE_END + "Run jbuild --help for usage.", USER_INPUT);
                }
            } else {
                artifacts.add(arg);
            }
        }

        return new FetchOptions(unmodifiableSet(artifacts), outDir == null ? "." : outDir);
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
                            LINE_END + "Run jbuild --help for usage.", USER_INPUT);
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
                            LINE_END + "Run jbuild --help for usage.", USER_INPUT);
                }
            } else if (expectRepoDir) {
                expectRepoDir = false;
                if (repoDir == null) {
                    repoDir = arg;
                } else {
                    throw new JBuildException("cannot provide repository directory more than once" +
                            LINE_END + "Run jbuild --help for usage.", USER_INPUT);
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
                            LINE_END + "Run jbuild --help for usage.", USER_INPUT);
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

        if (outDir == null && repoDir == null) outDir = InstallCommandExecutor.LIBS_DIR;
        if (outDir != null && repoDir != null) {
            throw new JBuildException("cannot specify both 'directory' and 'repository' options together." +
                    LINE_END + "Run jbuild --help for usage.", USER_INPUT);
        }

        return new InstallOptions(unmodifiableSet(artifacts), scopes, outDir, repoDir, optional);
    }

}

final class DoctorOptions {

    final String inputDir;
    final boolean interactive;
    final List<String> entryPoints;
    final Set<Pattern> typeExclusions;

    public DoctorOptions(String inputDir,
                         boolean interactive,
                         List<String> entryPoints,
                         Set<Pattern> typeExclusions) {
        this.inputDir = inputDir;
        this.interactive = interactive;
        this.entryPoints = unmodifiableList(entryPoints);
        this.typeExclusions = unmodifiableSet(typeExclusions);
    }

    static DoctorOptions parse(List<String> args) {
        String inputDir = null;
        var interactive = true;
        var entryPoints = new ArrayList<String>(4);
        var typeExclusions = new HashSet<String>(4);
        boolean expectEntryPoint = false, expectTypeExclusion = false;

        for (var arg : args) {
            if (expectEntryPoint) {
                expectEntryPoint = false;
                entryPoints.add(arg);
            } else if (expectTypeExclusion) {
                expectTypeExclusion = false;
                typeExclusions.add(arg);
            } else if (arg.startsWith("-")) {
                if (isEither(arg, "-y", "--yes")) {
                    interactive = false;
                } else if (isEither(arg, "-e", "--entrypoint")) {
                    expectEntryPoint = true;
                } else if (isEither(arg, "-x", "--exclude-type")) {
                    expectTypeExclusion = true;
                } else {
                    throw new JBuildException("invalid fix option: " + arg +
                            LINE_END + "Run jbuild --help for usage.", USER_INPUT);
                }
            } else {
                if (inputDir != null) {
                    throw new JBuildException("cannot provide more than one input directory for fix command" +
                            LINE_END + "Run jbuild --help for usage.", USER_INPUT);
                }
                inputDir = arg;
            }
        }

        if (expectEntryPoint) {
            throw new JBuildException("expecting value for '--entrypoint' option", USER_INPUT);
        }
        if (expectTypeExclusion) {
            throw new JBuildException("expecting value for '--exclude-type' option", USER_INPUT);
        }

        var exclusions = new HashSet<Pattern>(typeExclusions.size());
        for (var typeExclusion : typeExclusions) {
            try {
                exclusions.add(Pattern.compile(typeExclusion));
            } catch (PatternSyntaxException e) {
                throw new JBuildException("invalid regex: '" + typeExclusion + "': " + e, USER_INPUT);
            }
        }

        return new DoctorOptions(inputDir, interactive, entryPoints, exclusions);
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
                        LINE_END + "Run jbuild --help for usage.", USER_INPUT);
            } else {
                artifacts.add(arg);
            }
        }

        return new VersionsOptions(unmodifiableSet(artifacts));
    }

}

final class CompileOptions {
    final Set<String> inputDirectories;
    final Either<String, String> outputDirOrJar;
    final String classpath;

    public CompileOptions(Set<String> inputDirectories,
                          Either<String, String> outputDirOrJar,
                          String classpath) {
        this.inputDirectories = inputDirectories;
        this.outputDirOrJar = outputDirOrJar;
        this.classpath = classpath;
    }

    static CompileOptions parse(List<String> args) {
        Set<String> inputDirectories = new LinkedHashSet<>();
        String outputDir = null;
        String jar = null;
        var classpath = new StringBuilder();

        boolean waitingForClasspath = false, waitingForDirectory = false, waitingForJar = false;

        for (String arg : args) {
            if (waitingForClasspath) {
                waitingForClasspath = false;
                if (classpath.length() > 0) {
                    classpath.append(File.pathSeparatorChar);
                }
                classpath.append(arg);
            } else if (waitingForDirectory) {
                waitingForDirectory = false;
                outputDir = arg;
            } else if (waitingForJar) {
                waitingForJar = false;
                jar = arg;
            } else if (arg.startsWith("-")) {
                if (isEither(arg, "-cp", "--classpath")) {
                    waitingForClasspath = true;
                } else if (isEither(arg, "-d", "--directory")) {
                    if (outputDir != null) {
                        throw new JBuildException("cannot provide repository directory more than once" +
                                LINE_END + "Run jbuild --help for usage.", USER_INPUT);
                    }
                    waitingForDirectory = true;
                } else if (isEither(arg, "-j", "--jar")) {
                    if (jar != null) {
                        throw new JBuildException("cannot provide jar more than once" +
                                LINE_END + "Run jbuild --help for usage.", USER_INPUT);
                    }
                    waitingForJar = true;
                } else {
                    throw new JBuildException("invalid compile option: " + arg +
                            LINE_END + "Run jbuild --help for usage.", USER_INPUT);
                }
            } else {
                inputDirectories.add(arg);
            }
        }

        if (waitingForClasspath) {
            throw new JBuildException("expecting value for '--classpath' option", USER_INPUT);
        }
        if (waitingForDirectory) {
            throw new JBuildException("expecting value for '--directory' option", USER_INPUT);
        }
        if (waitingForJar) {
            throw new JBuildException("expecting value for '--jar' option", USER_INPUT);
        }
        if (outputDir != null && jar != null) {
            throw new JBuildException("cannot specify both 'directory' and 'jar' options together." +
                    LINE_END + "Run jbuild --help for usage.", USER_INPUT);
        }
        if (outputDir == null && jar == null) {
            jar = "";
        }
        return new CompileOptions(inputDirectories,
                outputDir != null ? Either.left(outputDir) : Either.right(jar),
                classpath.length() == 0 ? InstallCommandExecutor.LIBS_DIR : classpath.toString());
    }
}

