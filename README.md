# JBuild

JBuild is a toolkit for building Java and other JVM language based projects.

It consists of a simple CLI and can be easily used as a Java library, allowing JVM applications
to manage other Java projects and dependencies themselves.

## Features

- [x] fetch artifacts from file repositories.
- [x] fetch artifacts from HTTP repositories.
- [ ] fetch artifacts transitively.
- [x] list direct dependencies of artifacts.
- [x] list transitive dependencies of artifacts.
- [x] list available versions of artifacts.
- [ ] list artifacts licenses.
- [ ] install artifacts in local file repository.
- [ ] install artifacts in remote HTTP repository.
- [ ] check completeness of classpath in general (all code included).
- [ ] check completeness of classpath given a Java entry-point (exclude unused code).
- [ ] check binary compatibility of artifacts included in the classpath.
- [ ] automatically find compatible set of artifacts based on application needs.
- [ ] compile and package Java applications.

## Dependency Management

JBuild currently supports a sub-set of Maven, just enough to make sure most of the JVM ecosystem can be used without
trouble by JBuild users.

Here's the list of Maven tags and concepts supported by JBuild:

- [x] dependencies/dependency/[groupId, artifactId, version, scope]
- [ ] dependencies/dependency/exclusions
- [ ] optional dependencies
- [x] dependencyManagement
- [x] project properties
- [x] evaluate property placeholders from properties tag 
- [x] evaluate property placeholders from XML tags (version, artifactId, groupId)
- [x] parent POM
- [x] Maven BOM (dependencyManagement imports)

## CLI

```
------ JBuild CLI ------
Version: 0.0

Utility to build Java (JVM) applications.
This is work in progress!

Usage:
    jbuild <root-option> <cmd> <cmd-args...> 
Options:
    --verbose
    -V        log verbose output.
    --version
    -v        print JBuild version and exit.
    --help
    -h        print this usage message.

Available commands:

  * fetch
    Fetches Maven artifacts from the local Maven repo or Maven Central.      Usage:
        jbuild fetch <options... | artifact...>
      Options:
        --directory
        -d        output directory.
      Example:
        jbuild fetch -d libs org.apache.commons:commons-lang3:3.12.0

  * deps
    List the dependencies of the given artifacts.      Usage:
        jbuild deps <options... | artifact...>
      Options:
        --transitive
        -t        whether to list transitive dependencies.
      Example:
        jbuild deps com.google.guava:guava:31.0.1-jre junit:junit:4.13.2

  * versions
    List the versions of the given artifacts that are available on Maven Central.      Usage:
        jbuild versions <artifact...>
      Example:
        jbuild versions junit:junit
```

## Java API

The Javadocs will be published at some point.

For now, please refer to the CLI code, [jbuild.cli.Main](src/main/java/jbuild/cli/Main.java),
and [the tests](src/test/java/jbuild/).
