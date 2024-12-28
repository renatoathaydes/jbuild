# JBuild

[![Actions Status](https://github.com/renatoathaydes/jbuild/workflows/Build%20And%20Test%20on%20All%20OSs/badge.svg)](https://github.com/renatoathaydes/jbuild/actions)

[![Maven Central](https://img.shields.io/maven-central/v/com.athaydes.jbuild/jbuild.svg?label=Maven%20Central)](https://search.maven.org/search?q=a:jbuild%20g:com.athaydes.jbuild)

JBuild is a toolkit for building Java and other JVM language based projects, focussing on dependency management and
application's classpath verification.

It consists of a simple CLI, so it can be used as a very handy command-line utility, but can also be used as a Java
library, allowing JVM applications to manage other Java projects and dependencies themselves.

> If you're looking for a full build system, have a look at [jb](https://github.com/renatoathaydes/jb), which is built on top of JBuild!

## Features

- [x] fetch artifacts from file repositories.
- [x] fetch artifacts from HTTP repositories.
- [x] fetch artifacts transitively (install command).
- [x] specify file and HTTP repositories locations.
- [ ] fetch and check PGP signatures (done by `jb`).
- [x] list direct dependencies of artifacts.
- [x] list transitive dependencies of artifacts.
- [x] list available versions of artifacts.
- [x] detect version clashes in dependency tree.
- [x] list artifacts licenses.
- [x] install artifacts in local file system (flat dir or Maven repo).
- [x] check completeness of classpath given jar entry-points (exclude unused code).
- [x] automatically find compatible set of artifacts based on jar entry-points.
- [ ] show full call hierarchy of jar/class/method.
- [ ] find unused jars/classes/methods, given a classpath and jar entry-points.
- [ ] automatically prune classpath, removing unused jars.
- [x] compile and package Java applications.
- [ ] generate POM (done by `jb`).
- [x] optionally compile using `groovyc` (i.e. can handle mixed Groovy/Java code base).
- [ ] optionally compile using `kotlinc` (i.e. can handle mixed Kotlin/Java code base).

## Non-features

JBuild does not aim to replace Gradle or Maven, but to complement them by giving Java developers a simpler and faster
alternative for certain tasks, specially dependency management (both module- and code-level dependencies)
and classpath verification (something which can only be done when the full classpath is known, typically when
deploying a full application).

Features that fully-fledged build tools like Maven and Gradle want to have, but JBuild doesn't:

* general task management (e.g. Gradle tasks or Maven phases).
* plugins (JBuild can be used as a library - you don't need a JBuild plugin when you can have your own build 
  application that uses JBuild and other JVM libraries).
* configuration files (though JBuild may allow passing a file with CLI options in the future).
* anything not directly related to building Java or other JVM language applications.
* IDE integration (though JBuild makes it easy for IDEs to _see_ the classpath).

With that said, JBuild can easily be used in a more complete build tool, which is exactly what I did with [jb](https://github.com/renatoathaydes/jb).

## Dependency Management

JBuild currently supports a sub-set of Maven, just enough to make sure dependency resolution works
as accurately as with Maven.

Here's the list of Maven tags and concepts supported by JBuild:

- [x] dependencies/dependency/[groupId, artifactId, version, scope, optional]
- [x] dependencies/dependency/exclusions
- [x] dependencyManagement
- [x] dependency version ranges
- [x] project coordinates/packaging/parent
- [x] project license
- [x] project properties
- [x] evaluate property placeholders from properties tag 
- [x] evaluate property placeholders from XML tags (version, artifactId, groupId)
- [x] parent POM
- [x] Maven BOM (dependencyManagement imports)

> As JBuild is not designed to as a Maven replacement, Maven features
  not related to dependency resolution (which are needed by JBuild to resolve Java artifacts),
  are not expected to be added to JBuild. For example, Maven profiles and system dependencies will
  probably never be supported by JBuild.

## Code-level dependencies

JBuild can detect dependencies between Java types in a given classpath.

Currently implemented detections:

- [x] direct references to a Java class.
- [x] direct references to a Java interface.
- [x] direct references to a Java enum.
- [x] direct references to a Java type via array.
- [x] references to a Java type via extension (`implements` and `extends`).
- [x] references to a field.
- [x] references to a method by direct invocation.
- [x] references to a super-type method by virtual invocation.
- [x] references to a method via `MethodHandle` (used a lot with the stream API).
- [x] indirect type references via generics (if the type is actually used, it's detected by the other detections).
- [x] transitive code references.

## CLI

To install JBuild as a CLI, [download the jar from Maven Central](https://central.sonatype.com/artifact/com.athaydes.jbuild/jbuild/versions)
manually or using one of the commands below.

### Download using JBuild itself

```shell
jbuild fetch com.athaydes.jbuild:jbuild
```

### Download using mvn

```shell
# Download to the Maven Local Repository at ~/.m2/repository/
mvn dependency:get -Dartifact=com.athaydes.jbuild:jbuild:LATEST
# Copy the jar to the current directory
mvn dependency:copy  -Dartifact=com.athaydes.jbuild:jbuild:LATEST -DoutputDirectory=.
```

### Using the CLI

After downloading the jar, add an alias to run it with `java` to your `.bashrc|.zshrc` file as follows:

```
alias jbuild="java -jar $PATH_TO_JBUILD_JAR"
```

After that, you can run `jbuild` as any command in the shell. For example, to compile all Java source files
in the `src/` directory, run:

```shell
jbuild compile
```

To see help for a sub-command, like `compile`, run:

```shell
jbuild help compile
```

To see the general help page:

```shell
jbuild help # or -h
```

Which should print the following:

```
------ JBuild Basic CLI ------
       Version: 0.10.0
==============================

Utility to build Java (JVM) applications.

Usage:
    jbuild <root-options...> <cmd> <cmd-args...> 
Root Options:
    --quiet
     -q       print only minimum output.
    --repository
     -r       Maven repository to use to locate artifacts (file location or HTTP URL).
    --working-dir
     -w       The working directory to use.
    --verbose
    -V        log verbose output.
    --version
    -v        print JBuild version and exit.
    --help
    -h        print this usage message.

Available commands:

  * compile - compiles java source code
  * deps - lists dependencies of Maven artifacts
  * doctor - finds type-safe classpath given a set of jars
  * fetch - fetches Maven artifacts
  * install - installs Maven artifacts and dependencies into a flat dir or local Maven repo
  * requirements - finds type requirements of jars and class files
  * versions - list the versions of Maven artifacts
  * help - displays this help message or help for one of the other commands

Type 'jbuild help <command>' for more information about a command.

Artifact coordinates are given in the form <orgId>:<artifactId>[:<version>][:<ext>]
If the version is omitted, the latest available version is normally used.

Examples:
  # install latest version of Guava and all its dependencies in directory 'java-libs/'
  jbuild install com.google.guava:guava

  # show all version of javax.inject on the RedHat repository
  jbuild -r https://maven.repository.redhat.com/ga/ versions javax.inject:javax.inject

  # fetch the Guava POM
  jbuild fetch com.google.guava:guava:31.0.1-jre:pom

  # compile all Java sources in 'src/' or 'src/main/java' into a jar
  # using all jars in 'java-libs/' as the classpath
  jbuild compile
```

## Java API

[![javadoc](https://javadoc.io/badge2/com.athaydes.jbuild/jbuild/javadoc.svg)](https://javadoc.io/doc/com.athaydes.jbuild/jbuild)

Also refer to the CLI code, [jbuild.cli.Main](src/main/java/jbuild/cli/Main.java),
and [the tests](src/test/java/jbuild/) to learn more.

## JBuild sub-modules

This project has two sub-modules:

* [jbuild-api](jbuild-api/README.md) - extension API used by the `jb` tool to integrate with JBuild.
* [jbuild-classfile-parser](jbuild-classfile-parser/README.md) - JVM class file parser, used by JBuild to introspect JVM bytecode.

> Notice that JBuild is distributed as a single jar without any dependencies.
> Both sub-modules are included in the _main_ jar, but can be used independently if required.

## Building

The [jb](https://github.com/renatoathaydes/jb) build tool is used to build JBuild.

`jb` is a small executable that embeds JBuild to provide a modern, complete Java build tool.

> To install `jb`, get the [jb tar ball](https://github.com/renatoathaydes/jb/releases) and include `bin/jb` in your `PATH`.

To compile the jbuild jar `build/jbuild.jar`:

```shell
jb
```

To run unit tests.

```shell
jb -p src/test test
```

To run integration tests.

```shell
jb -p src/intTest test
```

### Bootstrapping

To compile JBuild using the `java` and `jar` commands and nothing else, run the [bootstrap.sh](boostrap.sh) script.

The script reads the following env vars:

* `BUILD_DIR` - directory into which to compile the class files.
* `JBUILD_JAR` - where to create the JBuild jar.
