# JBuild

[![Actions Status](https://github.com/renatoathaydes/jbuild/workflows/Build%20And%20Test%20on%20All%20OSs/badge.svg)](https://github.com/renatoathaydes/jbuild/actions)

[![Maven Central](https://img.shields.io/maven-central/v/com.athaydes.jbuild/jbuild.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.athaydes.jbuild%22%20AND%20a:%22jbuild%22)

> This project is NOT READY for general usage, consider it in ALPHA for the moment!

JBuild is a toolkit for building Java and other JVM language based projects, focussing on dependency management and
application's classpath verification.

It consists of a simple CLI, so it can be used as a very handy command-line utility, but can also be used as a Java
library, allowing JVM applications to manage other Java projects and dependencies themselves.

## Features

- [x] fetch artifacts from file repositories.
- [x] fetch artifacts from HTTP repositories.
- [x] fetch artifacts transitively (install command).
- [x] specify file and HTTP repositories locations.
- [ ] fetch and check PGP signatures.
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
- [ ] generate POM.

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

```
------ JBuild CLI ------
Version: 0.0

Utility to build Java (JVM) applications.
<<<< This is work in progress! >>>>

Usage:
    jbuild <root-options...> <cmd> <cmd-args...> 
Root Options:
    --repository
     -r       Maven repository to use to locate artifacts (file location or HTTP URL).
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
  * requirements - finds type requirements of jar(s)
  * versions - list the versions of Maven artifacts
  * help - displays this help message or help for one of the other commands

Type 'jbuild help <command>' for more information about a command.

Artifact coordinates are given in the form <orgId>:<artifactId>[:<version>][:<ext>]
If the version is omitted, the latest available version is normally used.

Examples:
  # install latest version of Guava and all its dependencies in directory 'java-libs/'
  jbuild install com.google.guava:guava

  # show all version of Spring available on the Spring repository
  jbuild versions -r https://repo.spring.io/artifactory/release/ org.springframework:spring-core

  # fetch the Guava POM
  jbuild fetch com.google.guava:guava:31.0.1-jre:pom

  # compile all Java sources in 'src/' or 'src/main/java' into a jar
  # using all jars in 'java-libs/' as the classpath
  jbuild compile
```

## Java API

The Javadocs will be published at some point.

For now, please refer to the CLI code, [jbuild.cli.Main](src/main/java/jbuild/cli/Main.java),
and [the tests](src/test/java/jbuild/).

## Building

[Task](https://taskfile.dev/usage/) is used to build JBuild. Task's role is basically to bootstrap JBuild,
then invoke jbuild commands, caching the results of each task to avoid re-running command unnecessarily.

To compile the jbuild jar (invokes `javac` directly):

```shell
task compile
```

After that, JBuild can self-compile:

```shell
task self-compile
```

To run unit tests.

```shell
task test
```

To run integration tests.

```shell
task integration-test
```
