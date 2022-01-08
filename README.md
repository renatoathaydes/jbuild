# JBuild

[![Actions Status](https://github.com/renatoathaydes/jbuild/workflows/Build%20And%20Test%20on%20All%20OSs/badge.svg)](https://github.com/renatoathaydes/jbuild/actions)

[![Maven Central](https://img.shields.io/maven-central/v/com.athaydes.jbuild/jbuild.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.athaydes.jbuild%22%20AND%20a:%22jbuild%22)

> This project is NOT READY for general usage, consider it in ALPHA for the moment!

JBuild is a toolkit for building Java and other JVM language based projects, focussing on dependency management and
application, as opposed to libraries, classpath verification.

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
- [ ] compile and package Java applications.
- [ ] execute JUnit tests.

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
- [ ] transitive code references.

## CLI

```
------ JBuild CLI ------
Version: 0.0

Utility to build Java (JVM) applications.
This is work in progress!

Usage:
    jbuild <root-option> <cmd> <cmd-args...> 
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

  * fetch
    Fetches Maven artifacts from the local Maven repo or Maven Central.
      Usage:
        jbuild fetch <options... | artifact...>
      Options:
        --directory
        -d        output directory.
      Example:
        jbuild fetch -d libs org.apache.commons:commons-lang3:3.12.0

  * install
    Install Maven artifacts from the local Maven repo or Maven Central.
    Unlike fetch, install downloads artifacts and their dependencies, and can write
    them into a flat directory or in the format of a Maven repository.
      Usage:
        jbuild install <options... | artifact...>
      Options:
        --directory
        -d        (flat) output directory.
        --repository
        -r        (Maven repository root) output directory.
        --optional
        -O        include optional dependencies.
        --scope
        -s        scope to include (can be passed more than once).
                  The runtime scope is used by default.
      Note:
        The --directory and --repository options are mutually exclusive.
        By default, the equivalent of '-d out/' is used.      Example:
        jbuild install -s compile org.apache.commons:commons-lang3:3.12.0

  * deps
    List the dependencies of the given artifacts.
      Usage:
        jbuild deps <options... | artifact...>
      Options:
        --licenses
        -l        show licenses of all artifacts (requires --transitive option).
        --optional
        -O        include optional dependencies.
        --scope
        -s        scope to include (can be passed more than once).
                  All scopes are listed by default.
        --transitive
        -t        include transitive dependencies.
      Example:
        jbuild deps com.google.guava:guava:31.0.1-jre junit:junit:4.13.2

  * doctor
    Examines a directory trying to find a consistent set of jars (classpath) for the entrypoint(s) jar(s).
    This command requires user interaction by default.
      Usage:
        jbuild doctor <options...> <dir>
      Options:
        --entrypoint
        -e        entry-point jar within the directory, or the application jar
                  (can be passed more than once).
        --yes
        -y        answer any question with 'yes'.
      Example:
        jbuild doctor my-dir -e app.jar

  * versions
    List the versions of the given artifacts that are available on Maven Central.
      Usage:
        jbuild versions <artifact...>
      Example:
        jbuild versions junit:junit
```

## Java API

The Javadocs will be published at some point.

For now, please refer to the CLI code, [jbuild.cli.Main](src/main/java/jbuild/cli/Main.java),
and [the tests](src/test/java/jbuild/).
