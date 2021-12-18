# JBuild

JBuild is a toolkit for building Java and other JVM language based projects, focussing on dependency management.

It consists of a simple CLI, so it can be used as a very handy CLI utility,
but can also be used as a Java library, allowing JVM applications
to manage other Java projects and dependencies themselves.

## Features

- [x] fetch artifacts from file repositories.
- [x] fetch artifacts from HTTP repositories.
- [x] fetch artifacts transitively (install command).
- [x] specify file and HTTP repositories locations.
- [ ] fetch PGP signatures.
- [x] list direct dependencies of artifacts.
- [x] list transitive dependencies of artifacts.
- [x] list available versions of artifacts.
- [ ] list artifacts licenses.
- [ ] accept local POM file as input for most commands.
- [x] install artifacts in local file system (flat dir or Maven repo).
- [ ] install artifacts in remote HTTP repository.
- [ ] check completeness of classpath in general (all code included).
- [ ] check completeness of classpath given a Java entry-point (exclude unused code).
- [ ] check binary compatibility of artifacts included in the classpath.
- [ ] detect version clashes in dependency tree.
- [ ] automatically find compatible set of artifacts in dependency tree, based on application needs.
- [ ] show full call hierarchy of jar/class/method.
- [ ] find unused jars/classes/methods in a given compilation unit.
- [ ] compile and package Java applications.

## Dependency Management

JBuild currently supports a sub-set of Maven, just enough to make sure dependency resolution works
as accurately as Maven.

Here's the list of Maven tags and concepts supported by JBuild:

- [x] dependencies/dependency/[groupId, artifactId, version, scope, optional]
- [x] dependencies/dependency/exclusions
- [x] dependencyManagement
- [x] project coordinates/packaging/parent
- [ ] project license
- [x] project properties
- [x] evaluate property placeholders from properties tag 
- [x] evaluate property placeholders from XML tags (version, artifactId, groupId)
- [x] parent POM
- [x] Maven BOM (dependencyManagement imports)

> As JBuild is not designed to as a Maven replacement, Maven features
  not related to dependency resolution (which are needed by JBuild to resolve Java artifacts),
  are not expected to be added to JBuild. For example, Maven profiles and system dependencies will
  probably never be supported by JBuild.

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
        --optional
        -O        include optional dependencies.
        --scope
        -s        scope to include (can be passed more than once).
                  All scopes are listed by default.
        --transitive
        -t        include transitive dependencies.
      Example:
        jbuild deps com.google.guava:guava:31.0.1-jre junit:junit:4.13.2

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
