package jbuild.cli;

import jbuild.api.JBuildException;
import jbuild.artifact.ArtifactRetriever;
import jbuild.artifact.file.FileArtifactRetriever;
import jbuild.artifact.http.HttpArtifactRetriever;
import jbuild.commands.IncrementalChanges;
import jbuild.commands.InstallCommandExecutor;
import jbuild.errors.ArtifactRetrievalError;
import jbuild.log.JBuildLog;
import jbuild.maven.DependencyExclusions;
import jbuild.maven.Scope;
import jbuild.util.Either;
import jbuild.util.TextUtils;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.toList;
import static jbuild.api.JBuildException.ErrorCause.USER_INPUT;
import static jbuild.cli.DepsOptions.createExclusionsFromOptions;
import static jbuild.cli.Options.compilePattern;
import static jbuild.util.CollectionUtils.lastOrDefault;
import static jbuild.util.FileUtils.relativize;
import static jbuild.util.TextUtils.LINE_END;
import static jbuild.util.TextUtils.isEither;

final class Options {

    final boolean verbose;
    final boolean help;
    final boolean version;
    final boolean quiet;
    final String workingDir;
    final String command;
    final List<String> repositories;
    final List<String> commandArgs;
    final List<String> applicationArgs;

    Options(boolean verbose,
            boolean help,
            boolean version,
            boolean quiet,
            String workingDir,
            String command,
            List<String> repositories,
            List<String> commandArgs,
            List<String> applicationArgs) {
        this.verbose = verbose;
        this.help = help;
        this.version = version;
        this.quiet = quiet;
        this.workingDir = workingDir;
        this.command = command;
        this.repositories = repositories;
        this.commandArgs = commandArgs;
        this.applicationArgs = applicationArgs;
    }

    List<ArtifactRetriever<? extends ArtifactRetrievalError>> getRetrievers(String workingDir, JBuildLog log) {
        return repositories.stream()
                .map(address -> {
                    if (TextUtils.isHttp(address)) {
                        return new HttpArtifactRetriever(log, address);
                    }
                    return new FileArtifactRetriever(Paths.get(relativize(workingDir, address)));
                }).collect(toList());
    }

    static Options parse(String[] args) {
        var repositories = new ArrayList<String>(4);
        boolean verbose = false, help = false, version = false, quiet = false;
        String command = "", workingDir = ".";
        boolean expectingRepository = false, expectingWorkingDir = false;
        int i;

        for (i = 0; i < args.length; i++) {
            String arg = args[i].trim();
            if (arg.isEmpty())
                continue;
            if (expectingRepository) {
                expectingRepository = false;
                repositories.add(arg);
            } else if (expectingWorkingDir) {
                expectingWorkingDir = false;
                workingDir = arg;
            } else if (!arg.startsWith("-")) {
                command = arg;
                i++;
                break;
            } else if (isEither(arg, "-r", "--repository")) {
                expectingRepository = true;
            } else if (isEither(arg, "-w", "--working-dir")) {
                expectingWorkingDir = true;
            } else if (isEither(arg, "-V", "--verbose")) {
                verbose = true;
            } else if (isEither(arg, "-v", "--version")) {
                version = true;
            } else if (isEither(arg, "-q", "--quiet")) {
                quiet = true;
            } else if (isEither(arg, "-h", "--help")) {
                help = true;
            } else {
                throw new JBuildException("invalid root option: " + arg + "." +
                        (!quiet ? LINE_END + "Run jbuild --help for usage." : ""), USER_INPUT);
            }
        }

        if (expectingRepository) {
            throw new JBuildException("expecting value for 'repository' option", USER_INPUT);
        }
        if (expectingWorkingDir) {
            throw new JBuildException("expecting value for 'working-dir' option", USER_INPUT);
        }

        List<String> commandArgs;
        if (i < args.length) {
            commandArgs = new ArrayList<>(args.length - i);
            for (; i < args.length; i++) {
                var arg = args[i].trim();
                if (arg.isEmpty())
                    continue;
                if ("--".equals(arg)) {
                    i++;
                    break;
                }
                commandArgs.add(args[i]);
            }
        } else {
            commandArgs = List.of();
        }

        List<String> applicationArgs;
        if (i < args.length) {
            applicationArgs = new ArrayList<>(args.length - i);
            for (; i < args.length; i++) {
                var arg = args[i].trim();
                if (arg.isEmpty())
                    continue;
                applicationArgs.add(args[i]);
            }
        } else {
            applicationArgs = List.of();
        }

        return new Options(verbose, help, version, quiet, workingDir,
                command, repositories, commandArgs, applicationArgs);
    }

    static Pattern compilePattern(String arg) {
        try {
            return Pattern.compile(arg);
        } catch (PatternSyntaxException e) {
            throw new JBuildException("Invalid regular expression: " + arg + ": " + e.getMessage(),
                    USER_INPUT);
        }
    }

}

final class FetchOptions {

    static final String NAME = "fetch";
    static final String DESCRIPTION = "fetches Maven artifacts";

    static final String USAGE = "  ## " + NAME + LINE_END +
            "    Fetches Maven artifacts from the local Maven repo or Maven Central." + LINE_END +
            "      Usage:" + LINE_END +
            "        jbuild " + NAME + " <options... | artifact...>" + LINE_END +
            "      Options:" + LINE_END +
            "        --directory" + LINE_END +
            "        -d <dir>  output directory (default: working directory)." + LINE_END +
            "      Note:" + LINE_END +
            "        Artifacts are declared using syntax:" +
            "            groupId:artifactId[:version[:extension[:classifier]]]" + LINE_END +
            "        If version is empty, the latest version is fetched." + LINE_END +
            "        If extension is empty, 'jar' is used." + LINE_END +
            "        Common classifiers are:" + LINE_END +
            "          - javadoc - fetches javadocs jar." + LINE_END +
            "          - sources - fetches Java sources jar." + LINE_END +
            "        Common extensions are:" + LINE_END +
            "          - jar      - the default." + LINE_END +
            "          - pom      - to fetch a POM." + LINE_END +
            "          - jar.asc  - the PGP signature for a jar." + LINE_END +
            "          - jar.sha1 - the SHA1 checksum for a jar." + LINE_END +
            "      Examples:" + LINE_END +
            "        jbuild " + NAME + " -d libs org.apache.commons:commons-lang3:3.12.0" + LINE_END +
            "        jbuild " + NAME + " junit:junit:::sources";

    final Set<String> artifacts;
    final String outDir;

    public FetchOptions(Set<String> artifacts, String outDir) {
        this.artifacts = artifacts;
        this.outDir = outDir;
    }

    static FetchOptions parse(List<String> args, boolean verbose) {
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
                    throw new JBuildException("invalid " + NAME + " option: " + arg + "." +
                            (verbose ? LINE_END + "Run jbuild --help for usage." : ""), USER_INPUT);
                }
            } else {
                artifacts.add(arg);
            }
        }

        return new FetchOptions(unmodifiableSet(artifacts), outDir == null ? "." : outDir);
    }

}

final class DepsOptions {

    static final String NAME = "deps";
    static final String DESCRIPTION = "lists dependencies of Maven artifacts";

    static final String USAGE = "  ## " + NAME + LINE_END +
            "    List the dependencies of the given artifacts." + LINE_END +
            "      Usage:" + LINE_END +
            "        jbuild " + NAME + " <options... | artifact...>" + LINE_END +
            "      Options:" + LINE_END +
            "        --extra" + LINE_END +
            "        -e        show extra information from the POMs (e.g. dependency-management)." + LINE_END +
            "        --pom" + LINE_END +
            "        -p        show dependencies of a local POM." + LINE_END +
            "        --licenses" + LINE_END +
            "        -l        show licenses of all artifacts (requires --transitive option)." + LINE_END +
            "        --optional" + LINE_END +
            "        -O        include optional dependencies." + LINE_END +
            "        --scope" + LINE_END +
            "        -s <scope> scope to include (can be passed more than once)." + LINE_END +
            "                  All scopes are listed by default." + LINE_END +
            "        --transitive" + LINE_END +
            "        -t        include transitive dependencies." + LINE_END +
            "        --exclusion" + LINE_END +
            "        -x <regex> dependency exclusion regex pattern, matches against coordinates" + LINE_END +
            "                   (can be passed more than once). Each exclusion applies to the previously" + LINE_END +
            "                   listed artifact, or globally if not preceded by any artifact." + LINE_END +
            "      Example:" + LINE_END +
            "        jbuild " + NAME + " com.google.guava:guava:31.0.1-jre junit:junit:4.13.2";

    final Set<String> artifacts;
    final EnumSet<Scope> scopes;
    final DependencyExclusions exclusions;
    final String pom;
    final boolean transitive;
    final boolean optional;
    final boolean licenses;
    final boolean showExtra;

    DepsOptions(Set<String> artifacts,
                EnumSet<Scope> scopes,
                DependencyExclusions exclusions,
                String pom,
                boolean transitive,
                boolean optional,
                boolean licenses,
                boolean showExtra) {
        this.artifacts = artifacts;
        this.scopes = scopes;
        this.exclusions = exclusions;
        this.pom = pom;
        this.transitive = transitive;
        this.optional = optional;
        this.licenses = licenses;
        this.showExtra = showExtra;
    }

    static DepsOptions parse(List<String> args, boolean verbose) {
        var artifacts = new LinkedHashSet<String>();
        var scopes = EnumSet.noneOf(Scope.class);
        var exclusions = new LinkedHashMap<String, Set<Pattern>>();
        String pom = "";
        boolean transitive = false, optional = false, expectExclusion = false,
                licenses = false, expectScope = false, showExtra = false, expectPom = false;

        for (String arg : args) {
            if (expectScope) {
                expectScope = false;
                try {
                    scopes.add(Scope.valueOf(arg.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    throw new JBuildException("invalid scope value: '" + arg +
                            "'. Acceptable values are: " + Arrays.toString(Scope.values()), USER_INPUT);
                }
            } else if (expectExclusion) {
                expectExclusion = false;
                exclusions.computeIfAbsent(lastOrDefault(artifacts, ""), ignore -> new HashSet<>(2))
                        .add(compilePattern(arg));
            } else if (expectPom) {
                expectPom = false;
                pom = arg;
            } else if (arg.startsWith("-")) {
                if (isEither(arg, "-s", "--scope")) {
                    expectScope = true;
                } else if (isEither(arg, "-O", "--optional")) {
                    optional = true;
                } else if (isEither(arg, "-t", "--transitive")) {
                    transitive = true;
                } else if (isEither(arg, "-l", "--licenses")) {
                    licenses = true;
                } else if (isEither(arg, "-e", "--extra")) {
                    showExtra = true;
                } else if (isEither(arg, "-p", "--pom")) {
                    if (!pom.isBlank()) {
                        throw new JBuildException("Providing more than one POM is not allowed." +
                                (verbose ? LINE_END + "Run jbuild --help for usage." : ""), USER_INPUT);
                    }
                    expectPom = true;
                } else if (isEither(arg, "-x", "--exclusion")) {
                    expectExclusion = true;
                } else {
                    throw new JBuildException("invalid " + NAME + " option: " + arg + "." +
                            (verbose ? LINE_END + "Run jbuild --help for usage." : ""), USER_INPUT);
                }
            } else {
                artifacts.add(arg);
            }
        }

        if (expectScope) {
            throw new JBuildException("expecting value for 'scope' option", USER_INPUT);
        }
        if (expectExclusion) {
            throw new JBuildException("expecting value for 'exclusion' option", USER_INPUT);
        }

        // if no scopes are included explicitly, use all
        if (scopes.isEmpty())
            scopes = EnumSet.allOf(Scope.class);

        return new DepsOptions(unmodifiableSet(artifacts), scopes, createExclusionsFromOptions(exclusions),
                pom, transitive, optional, licenses, showExtra);
    }

    static DependencyExclusions createExclusionsFromOptions(Map<String, Set<Pattern>> exclusions) {
        var globalExclusions = exclusions.remove("");
        return new DependencyExclusions(globalExclusions == null ? Set.of() : globalExclusions, exclusions);
    }

}

final class InstallOptions {

    static final String NAME = "install";
    static final String DESCRIPTION = "installs Maven artifacts and dependencies into a flat dir or local Maven repo";

    static final String USAGE = "  ## " + NAME + LINE_END +
            "    Install Maven artifacts from local or remote Maven repositories." + LINE_END +
            "    Unlike " + FetchOptions.NAME + ", install downloads artifacts and their dependencies, and can write"
            + LINE_END +
            "    them into a flat directory or in the format of a Maven repository." + LINE_END +
            "      Usage:" + LINE_END +
            "        jbuild " + NAME + " <options... | artifact...>" + LINE_END +
            "      Options:" + LINE_END +
            "        --directory" + LINE_END +
            "        -d <dir>  (flat) output directory (default: java-libs)." + LINE_END +
            "        --repository" + LINE_END +
            "        -r <dir>  (Maven repository root) output directory." + LINE_END +
            "        --non-transitive" + LINE_END +
            "        -n        install direct dependencies only." + LINE_END +
            "        --maven-local" + LINE_END +
            "        -m        Install (also) on the local Maven repository (~/.m2/repository)." + LINE_END +
            "        --optional" + LINE_END +
            "        -O        include optional dependencies." + LINE_END +
            "        --scope" + LINE_END +
            "        -s <scope> scope to include (can be passed more than once)." + LINE_END +
            "                  The runtime scope is used by default." + LINE_END +
            "        --exclusion" + LINE_END +
            "        -x <regex> dependency exclusion regex pattern, matches against coordinates" + LINE_END +
            "                   (can be passed more than once). Each exclusion applies to the previously" + LINE_END +
            "                   listed artifact, or globally if not preceded by any artifact." + LINE_END +
            "        -c" + LINE_END +
            "        --checksum download and verify the checksum of all artifacts." + LINE_END +
            "      Note:" + LINE_END +
            "        The --directory and --repository options are mutually exclusive." + LINE_END +
            "        If the --maven-local flag is used, then artifacts are installed at ~/.m2/repository" + LINE_END +
            "        (or MAVEN_HOME) and any other location given." + LINE_END +
            "        The --non-transitive option cannot be used together with the --maven-local option." + LINE_END +
            "        By default, the equivalent of '-d java-libs/' is used." + LINE_END +
            "      Examples:" + LINE_END +
            "        # install Apache commons-lang3 and its transitive compile-scope dependencies" + LINE_END +
            "        jbuild " + NAME + " -s compile org.apache.commons:commons-lang3:3.12.0" + LINE_END +
            "        # install Guava and Xalan, but exclude all dependencies like" + LINE_END +
            "        # 'org.apache.*' anywhere, and all like '.*findbugs.*' from the Guava dependency tree" + LINE_END +
            "        jbuild " + NAME + " -x 'org.apache.*' com.google.guava:guava:31.0.1-jre -x '.*findbugs.*'";

    final Set<String> artifacts;
    final DependencyExclusions exclusions;
    final EnumSet<Scope> scopes;
    final String outDir;
    final String repoDir;
    final boolean optional, transitive, mavenLocal, checksum;

    InstallOptions(Set<String> artifacts,
                   DependencyExclusions exclusions,
                   EnumSet<Scope> scopes,
                   String outDir,
                   String repoDir,
                   boolean optional,
                   boolean transitive,
                   boolean mavenLocal,
                   boolean checksum) {
        this.artifacts = artifacts;
        this.exclusions = exclusions;
        this.scopes = scopes;
        this.outDir = outDir;
        this.repoDir = repoDir;
        this.optional = optional;
        this.transitive = transitive;
        this.mavenLocal = mavenLocal;
        this.checksum = checksum;
    }

    static InstallOptions parse(List<String> args, boolean verbose) {
        var artifacts = new LinkedHashSet<String>(4);
        var exclusions = new LinkedHashMap<String, Set<Pattern>>(2);
        var scopes = EnumSet.noneOf(Scope.class);
        var optional = false;
        String outDir = null, repoDir = null;
        boolean expectScope = false,
                expectOutDir = false,
                expectRepoDir = false,
                expectExclusion = false,
                transitive = true,
                mavenLocal = false,
                checksum = false;

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
                    throw new JBuildException("cannot provide output directory more than once." +
                            (verbose ? LINE_END + "Run jbuild --help for usage." : ""), USER_INPUT);
                }
            } else if (expectRepoDir) {
                expectRepoDir = false;
                if (repoDir == null) {
                    repoDir = arg;
                } else {
                    throw new JBuildException("cannot provide repository directory more than once." +
                            (verbose ? LINE_END + "Run jbuild --help for usage." : ""), USER_INPUT);
                }
            } else if (expectExclusion) {
                expectExclusion = false;
                exclusions.computeIfAbsent(lastOrDefault(artifacts, ""), ignore -> new HashSet<>(2))
                        .add(compilePattern(arg));
            } else if (arg.startsWith("-")) {
                if (isEither(arg, "-s", "--scope")) {
                    expectScope = true;
                } else if (isEither(arg, "-O", "--optional")) {
                    optional = true;
                } else if (isEither(arg, "-d", "--directory")) {
                    expectOutDir = true;
                } else if (isEither(arg, "-r", "--repository")) {
                    expectRepoDir = true;
                } else if (isEither(arg, "-m", "--maven-local")) {
                    mavenLocal = true;
                } else if (isEither(arg, "-c", "--checksum")) {
                    checksum = true;
                } else if (isEither(arg, "-n", "--non-transitive")) {
                    transitive = false;
                } else if (isEither(arg, "-x", "--exclusion")) {
                    expectExclusion = true;
                } else {
                    throw new JBuildException("invalid " + NAME + " option: " + arg + "." +
                            (verbose ? LINE_END + "Run jbuild --help for usage." : ""), USER_INPUT);
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
        if (scopes.isEmpty())
            scopes = EnumSet.of(Scope.RUNTIME);
        if (outDir == null && repoDir == null && !mavenLocal)
            outDir = InstallCommandExecutor.LIBS_DIR;
        if (outDir != null && repoDir != null) {
            throw new JBuildException("cannot specify both 'directory' and 'repository' options together." +
                    (verbose ? LINE_END + "Run jbuild --help for usage." : ""), USER_INPUT);
        }
        if (!transitive && mavenLocal) {
            throw new JBuildException("cannot specify both 'non-transitive' and 'maven-local' options together." +
                    (verbose ? LINE_END + "Run jbuild --help for usage." : ""), USER_INPUT);
        }
        if (!transitive && repoDir != null) {
            throw new JBuildException("cannot specify both 'non-transitive' and 'repository' options together." +
                    (verbose ? LINE_END + "Run jbuild --help for usage." : ""), USER_INPUT);
        }

        return new InstallOptions(unmodifiableSet(artifacts), createExclusionsFromOptions(exclusions),
                scopes, outDir, repoDir, optional, transitive, mavenLocal, checksum);
    }

}

final class DoctorOptions {

    static final String NAME = "doctor";
    static final String DESCRIPTION = "finds type-safe classpath given a set of jars";

    static final String USAGE = "  ## " + NAME + LINE_END +
            "    Examines a directory trying to find a consistent set of jars (classpath) for the entrypoint(s) jar(s)."
            + LINE_END +
            "      Usage:" + LINE_END +
            "        jbuild " + NAME + " <options...> <dir>" + LINE_END +
            "      Options:" + LINE_END +
            "        --entrypoint" + LINE_END +
            "        -e <file> entry-point jar within the directory, or the application jar" + LINE_END +
            "                  (can be passed more than once)." + LINE_END +
            "        --exclude-type" + LINE_END +
            "        -x <regex> exclude type from analysis, allowing it to be missing" + LINE_END +
            "                  (can be passed more than once)." + LINE_END +
            "      Example:" + LINE_END +
            "        jbuild " + NAME + " java-libs -e app.jar";

    final String inputDir;
    final List<String> entryPoints;
    final Set<Pattern> typeExclusions;

    public DoctorOptions(String inputDir,
                         List<String> entryPoints,
                         Set<Pattern> typeExclusions) {
        this.inputDir = inputDir;
        this.entryPoints = unmodifiableList(entryPoints);
        this.typeExclusions = unmodifiableSet(typeExclusions);
    }

    static DoctorOptions parse(List<String> args, boolean verbose) {
        String inputDir = null;
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
                if (isEither(arg, "-e", "--entrypoint")) {
                    expectEntryPoint = true;
                } else if (isEither(arg, "-x", "--exclude-type")) {
                    expectTypeExclusion = true;
                } else {
                    throw new JBuildException("invalid " + NAME + " option: " + arg + "." +
                            (verbose ? LINE_END + "Run jbuild --help for usage." : ""), USER_INPUT);
                }
            } else {
                if (inputDir != null) {
                    throw new JBuildException("cannot provide more than one input directory for fix command." +
                            (verbose ? LINE_END + "Run jbuild --help for usage." : ""), USER_INPUT);
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

        return new DoctorOptions(inputDir, entryPoints, exclusions);
    }
}

final class RequirementsOptions {

    static final String NAME = "requirements";
    static final String DESCRIPTION = "finds type requirements of jars and class files";

    static final String USAGE = "  ## " + NAME + LINE_END +
            "    " + LINE_END +
            "    Determines the Java types required by a set of jars or class files." + LINE_END +
            "      Usage:" + LINE_END +
            "        jbuild " + NAME + "<options...> <file...>" + LINE_END +
            "      Options:" + LINE_END +
            "        --per-class" + LINE_END +
            "        -c        show intra-jar requirements per-class" + LINE_END +
            "      Example:" + LINE_END +
            "        jbuild " + NAME + " app.jar lib.jar";

    final Set<String> files;
    final boolean perClass;

    public RequirementsOptions(Set<String> files, boolean perClass) {
        this.files = unmodifiableSet(files);
        this.perClass = perClass;
    }

    static RequirementsOptions parse(List<String> args, boolean verbose) {
        var files = new LinkedHashSet<String>(4);
        var perClass = false;

        for (var arg : args) {
            if (arg.startsWith("-")) {
                if (isEither(arg, "-c", "--per-class")) {
                    perClass = true;
                } else {
                    throw new JBuildException("invalid " + NAME + " option: " + arg + "." +
                            (verbose ? LINE_END + "Run jbuild --help for usage." : ""), USER_INPUT);
                }
            } else {
                files.add(arg);
            }
        }

        return new RequirementsOptions(files, perClass);
    }
}

final class VersionsOptions {

    static final String NAME = "versions";
    static final String DESCRIPTION = "list the versions of Maven artifacts";

    static final String USAGE = "  ## " + NAME + LINE_END +
            "    List the versions of the given artifacts that are available on configured repositories." + LINE_END +
            "      Usage:" + LINE_END +
            "        jbuild " + NAME + " <artifact...>" + LINE_END +
            "      Example:" + LINE_END +
            "        jbuild " + NAME + " junit:junit";

    final Set<String> artifacts;

    public VersionsOptions(Set<String> artifacts) {
        this.artifacts = artifacts;
    }

    static VersionsOptions parse(List<String> args, boolean verbose) {
        var artifacts = new LinkedHashSet<String>();

        for (String arg : args) {
            if (arg.startsWith("-")) {
                throw new JBuildException("invalid " + NAME + " option: " + arg + "." +
                        (verbose ? LINE_END + "Run jbuild --help for usage." : ""), USER_INPUT);
            } else {
                artifacts.add(arg);
            }
        }

        return new VersionsOptions(unmodifiableSet(artifacts));
    }

}

final class CompileOptions {

    static final String NAME = "compile";
    static final String DESCRIPTION = "compiles java source code";

    static final String USAGE = "  ## " + NAME + LINE_END +
            "    Compile all Java source files found the input directories." + LINE_END +
            "      Usage:" + LINE_END +
            "        jbuild " + NAME + " <options... | input-directory...> [-- <javac-args>]" + LINE_END +
            "      Options:" + LINE_END +
            "        --classpath" + LINE_END +
            "        -cp <paths> Java classpath (may be given more than once; default: java-libs/*)." + LINE_END +
            "        --directory" + LINE_END +
            "        -d        output directory, where to put class files on." + LINE_END +
            "        --resources" + LINE_END +
            "        -r <dir>  resources directory, files are copied unmodified with class files." + LINE_END +
            "        --jar" + LINE_END +
            "        --groovy" + LINE_END +
            "        -g <groovy-jar> compile with the Groovy compiler." + LINE_END +
            "        -j <file> destination jar (default: <working-directory>.jar)." + LINE_END +
            "        --sources-jar" + LINE_END +
            "        -sj       whether to create a sources jar (name: <jar-name>-sources.jar)." + LINE_END +
            "        --javadoc-jar" + LINE_END +
            "        -dj       whether to create a javadocs jar (name: <jar-name>-javadoc.jar)." + LINE_END +
            "        --jb-extension" + LINE_END +
            "        -x        generate jb extension manifest (for use with the jb tool)." + LINE_END +
            "        --main-class" + LINE_END +
            "        -m <name> application's main class." + LINE_END +
            "        --manifest" + LINE_END +
            "        -mf <name> manifest file to pass to the jar command." + LINE_END +
            "            If the name \"-\" is used, no manifest is generated." + LINE_END +
            "      Incremental compilation options:" + LINE_END +
            "        --deleted <file>  deleted file since last compilation." + LINE_END +
            "        --added <file>    added/modified file since last compilation." + LINE_END +
            "      Note:" + LINE_END +
            "        The --directory and --jar options are mutually exclusive." + LINE_END +
            "        By default, the equivalent of '-j <working-directory>.jar -cp java-libs' is used," + LINE_END +
            "        with sources read from either src/main/java, src/ or the working-directory." + LINE_END +
            "        To pass further arguments directly to javac, use -- <args>." + LINE_END +
            "        Default javac options used are: '-encoding utf-8 -Werr -parameters'." + LINE_END +
            "        Passing javac classpath options explicitly overrides jbuild's -cp." + LINE_END +
            "      Example:" + LINE_END +
            "        jbuild " + NAME + " -cp libs/jsr305-3.0.2.jar -- --release 11";

    final Set<String> inputDirectories;
    final Set<String> resourcesDirectories;
    final Either<String, String> outputDirOrJar;
    final String mainClass;
    final String groovyJar;
    final boolean generateJbManifest;
    final boolean createSourcesJar;
    final boolean createJavadocsJar;
    final String classpath;
    final Either<Boolean, String> manifest;
    final IncrementalChanges incrementalChanges;

    public CompileOptions(Set<String> inputDirectories,
                          Set<String> resourcesDirectories,
                          Either<String, String> outputDirOrJar,
                          String mainClass,
                          String groovyJar,
                          boolean generateJbManifest,
                          boolean createSourcesJar,
                          boolean createJavadocsJar,
                          String classpath,
                          Either<Boolean, String> manifest,
                          IncrementalChanges incrementalChanges) {
        this.inputDirectories = inputDirectories;
        this.resourcesDirectories = resourcesDirectories;
        this.outputDirOrJar = outputDirOrJar;
        this.mainClass = mainClass;
        this.groovyJar = groovyJar;
        this.generateJbManifest = generateJbManifest;
        this.createSourcesJar = createSourcesJar;
        this.createJavadocsJar = createJavadocsJar;
        this.classpath = classpath;
        this.manifest = manifest;
        this.incrementalChanges = incrementalChanges;
    }

    static CompileOptions parse(List<String> args, boolean verbose) {
        Set<String> inputDirectories = new LinkedHashSet<>(2);
        Set<String> resourcesDirectories = new LinkedHashSet<>(2);
        Set<String> deletedFiles = new LinkedHashSet<>(2);
        Set<String> addedFiles = new LinkedHashSet<>(2);
        String outputDir = null, jar = null, mainClass = null, groovyJar = null;
        Either<Boolean, String> manifest = null;
        var classpath = new StringBuilder();

        boolean waitingForClasspath = false,
                waitingForDirectory = false,
                waitingForResources = false,
                waitingForJar = false,
                waitingForMainClass = false,
                waitingForGroovyJar = false,
                waitingForManifest = false,
                waitingForDeleted = false,
                waitingForAdded = false,
                generateJbManifest = false,
                createSourcesJar = false,
                createJavadocsJar = false;

        for (String arg : args) {
            if (waitingForClasspath) {
                waitingForClasspath = false;
                for (String part : arg.split("[;:]", -1)) {
                    if (part.isBlank())
                        continue;
                    if (classpath.length() > 0) {
                        classpath.append(File.pathSeparatorChar);
                    }
                    classpath.append(part);
                }
            } else if (waitingForDirectory) {
                waitingForDirectory = false;
                outputDir = arg;
            } else if (waitingForResources) {
                waitingForResources = false;
                for (String part : arg.split("[;:]", -1)) {
                    if (part.isBlank())
                        continue;
                    resourcesDirectories.add(part);
                }
            } else if (waitingForJar) {
                waitingForJar = false;
                jar = arg;
            } else if (waitingForMainClass) {
                waitingForMainClass = false;
                mainClass = arg;
            } else if (waitingForManifest) {
                waitingForManifest = false;
                manifest = "-".equals(arg) ? Either.left(false) : Either.right(arg);
            } else if (waitingForDeleted) {
                waitingForDeleted = false;
                deletedFiles.add(arg);
            } else if (waitingForAdded) {
                waitingForAdded = false;
                addedFiles.add(arg);
            } else if (waitingForGroovyJar) {
                waitingForGroovyJar = false;
                groovyJar = arg;
            } else if (arg.startsWith("-")) {
                if (isEither(arg, "-cp", "--classpath")) {
                    waitingForClasspath = true;
                } else if (isEither(arg, "-x", "--jb-extension")) {
                    generateJbManifest = true;
                } else if (isEither(arg, "-sj", "--sources-jar")) {
                    createSourcesJar = true;
                } else if (isEither(arg, "-dj", "--javadoc-jar")) {
                    createJavadocsJar = true;
                } else if (isEither(arg, "-r", "--resources")) {
                    waitingForResources = true;
                } else if (isEither(arg, "-g", "--groovy")) {
                    if (groovyJar != null) {
                        throw new JBuildException("cannot provide groovy option more than once." +
                                (verbose ? LINE_END + "Run jbuild --help for usage." : ""), USER_INPUT);
                    }
                    waitingForGroovyJar = true;
                } else if (isEither(arg, "-d", "--directory")) {
                    if (outputDir != null) {
                        throw new JBuildException("cannot provide repository directory more than once." +
                                (verbose ? LINE_END + "Run jbuild --help for usage." : ""), USER_INPUT);
                    }
                    waitingForDirectory = true;
                } else if (isEither(arg, "-j", "--jar")) {
                    if (jar != null) {
                        throw new JBuildException("cannot provide jar more than once." +
                                (verbose ? LINE_END + "Run jbuild --help for usage." : ""), USER_INPUT);
                    }
                    waitingForJar = true;
                } else if (isEither(arg, "-m", "--main-class")) {
                    if (mainClass != null) {
                        throw new JBuildException("cannot provide main-class more than once." +
                                (verbose ? LINE_END + "Run jbuild --help for usage." : ""), USER_INPUT);
                    }
                    waitingForMainClass = true;
                } else if (isEither(arg, "-mf", "--manifest")) {
                    if (manifest != null) {
                        throw new JBuildException("cannot provide manifest more than once." +
                                (verbose ? LINE_END + "Run jbuild --help for usage." : ""), USER_INPUT);
                    }
                    waitingForManifest = true;
                } else if ("--added".equals(arg)) {
                    waitingForAdded = true;
                } else if ("--deleted".equals(arg)) {
                    waitingForDeleted = true;
                } else {
                    throw new JBuildException("invalid " + NAME + " option: " + arg + "." +
                            (verbose ? LINE_END + "Run jbuild --help for usage." : ""), USER_INPUT);
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
        if (waitingForMainClass) {
            throw new JBuildException("expecting value for '--main-class' option", USER_INPUT);
        }
        if (waitingForGroovyJar) {
            throw new JBuildException("expecting value for '--groovy' option", USER_INPUT);
        }
        if (waitingForManifest) {
            throw new JBuildException("expecting value for '--manifest' option", USER_INPUT);
        }
        if (waitingForDeleted) {
            throw new JBuildException("expecting value for '--deleted' option", USER_INPUT);
        }
        if (waitingForAdded) {
            throw new JBuildException("expecting value for '--added' option", USER_INPUT);
        }
        if (outputDir != null && jar != null) {
            throw new JBuildException("cannot specify both 'directory' and 'jar' options together." +
                    (verbose ? LINE_END + "Run jbuild --help for usage." : ""), USER_INPUT);
        }
        if (outputDir == null && jar == null) {
            jar = "";
        }
        if (outputDir != null && (createJavadocsJar || createSourcesJar)) {
            throw new JBuildException("the --sources-jar and --javadoc-jar options are only allowed " +
                    "when compiling to a jar, hence cannot be used with the --directory option.", USER_INPUT);
        }

        IncrementalChanges incrementalChanges =
                (deletedFiles.isEmpty() && addedFiles.isEmpty())
                        ? null
                        : new IncrementalChanges(deletedFiles, addedFiles);

        return new CompileOptions(inputDirectories,
                resourcesDirectories,
                outputDir != null ? Either.left(outputDir) : Either.right(jar),
                mainClass == null ? "" : mainClass,
                groovyJar == null ? "" : groovyJar,
                generateJbManifest,
                createSourcesJar,
                createJavadocsJar,
                classpath.length() == 0 ? InstallCommandExecutor.LIBS_DIR : classpath.toString(),
                manifest == null ? Either.left(true) : manifest,
                incrementalChanges);
    }
}
