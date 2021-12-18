package jbuild.cli;

import jbuild.artifact.Artifact;
import jbuild.log.JBuildLog;
import jbuild.maven.ArtifactKey;
import jbuild.maven.Dependency;
import jbuild.maven.DependencyTree;
import jbuild.maven.License;
import jbuild.maven.Scope;
import jbuild.util.NonEmptyCollection;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static jbuild.util.CollectionUtils.sorted;

final class DependencyTreeLogger {

    private static final String INDENT = "    ";

    private final JBuildLog log;
    private final DepsOptions options;

    DependencyTreeLogger(JBuildLog log, DepsOptions options) {
        this.log = log;
        this.options = options;
    }

    public void log(DependencyTree tree) {
        log.print("Dependencies of " + tree.root.artifact.getCoordinates());

        if (options.transitive && options.optional) {
            log.print(" (incl. transitive, optionals)");
        } else if (options.transitive) {
            log.print(" (incl. transitive)");
        } else if (options.optional) {
            log.print(" (incl. optionals)");
        }

        if (options.licenses && !tree.root.pom.getLicenses().isEmpty()) {
            log.print(() -> displayLicenses(tree.root.pom.getLicenses()));
        }

        log.println(":");

        var deps = tree.root.pom.getDependencies(options.scopes, options.optional);

        if (deps.isEmpty()) {
            log.println("  * no dependencies");
            return;
        }

        var groupedDeps = deps.stream()
                .collect(groupingBy(dep -> dep.scope));

        for (Scope scope : options.scopes) {
            var scopeDeps = groupedDeps.get(scope);
            if (scopeDeps != null && !scopeDeps.isEmpty()) {
                int dependencyCount;
                log.println("  - scope " + scope);
                if (options.transitive) {
                    var visitedDeps = new HashSet<Artifact>();
                    logTree(visitedDeps, scopeDeps, tree.dependencies, tree.root.pom.getLicenses(), INDENT, scope);
                    dependencyCount = visitedDeps.size();
                } else {
                    logChildren(scopeDeps);
                    dependencyCount = scopeDeps.size();
                }
                log.println(() -> "  " + dependencyCount + " " + scope +
                        " dependenc" + (dependencyCount == 1 ? "y" : "ies") + " listed");
            }
        }
    }

    private void logChildren(Collection<Dependency> children) {
        for (var child : sorted(children, comparing(dep -> dep.artifact.getCoordinates()))) {
            log.println(displayDependency(INDENT, child));
        }
    }

    private void logTree(Set<Artifact> visitedDeps,
                         Collection<Dependency> scopeDeps,
                         List<DependencyTree> children,
                         Set<License> licenses,
                         String indent,
                         Scope scope) {
        var childByKey = children.stream()
                .collect(toMap(c -> ArtifactKey.of(c.root.artifact),
                        NonEmptyCollection::of, NonEmptyCollection::of));

        for (var dep : sorted(scopeDeps, comparing(dep -> dep.artifact.getCoordinates()))) {
            log.print(() -> displayDependency(indent, dep));

            var isNew = visitedDeps.add(dep.artifact);

            if (isNew) {
                var nextBranch = childByKey.get(ArtifactKey.of(dep));
                if (nextBranch == null) {
                    log.println(" (X)");
                } else {
                    if (options.licenses && !licenses.isEmpty()) {
                        log.println(() -> displayLicenses(licenses));
                    } else {
                        log.println("");
                    }
                    for (var next : nextBranch) {
                        var nextDeps = next.root.pom
                                .getDependencies(scope.transitiveScopes(), options.optional);
                        logTree(visitedDeps, nextDeps, next.dependencies,
                                next.root.pom.getLicenses(), indent + INDENT, scope);
                    }
                }
            } else {
                log.println(" (-)");
            }
        }
    }

    private String displayLicenses(Set<License> licenses) {
        return licenses.stream()
                .map(lic -> '{' + licenseString(lic) + '}')
                .collect(joining(", ", " [", "]"));
    }

    private String displayDependency(String indent, Dependency dep) {
        return indent + "* " + dep.artifact.getCoordinates() +
                " [" + dep.scope + "]" +
                (dep.optional ? "[optional]" : "");
    }

    private static String licenseString(License license) {
        switch (license.name) {
            case "The Apache License, Version 1.0":
            case "Apache License 1.0":
            case "Apache-1.0":
                return "Apache-1.0";
            case "The Apache License, Version 1.1":
            case "Apache License 1.1":
            case "Apache-1.1":
                return "Apache-1.1";
            case "The Apache License, Version 2.0":
            case "Apache License 2.0":
            case "Apache-2.0":
                return "Apache-2.0";
            case "Do What The F*ck You Want To Public License":
            case "WTFPL":
                return "WTFPL";
            case "The New BSD License":
            case "BSD 2-Clause \"Simplified\" License":
            case "BSD-2-Clause":
                return "BSD-2-Clause";
            case "The MIT License":
            case "MIT License":
            case "MIT":
                return "MIT";
            case "Common Public Attribution License 1.0":
            case "CPAL-1.0":
                return "CPAL-1.0";
            case "Common Public License Version 1.0":
            case "CPL-1.0":
                return "CPL-1.0";
            case "GNU General Public License v1.0 only":
            case "GPL-1.0":
                return "GPL-1.0";
            case "GNU General Public License v1.0 or later":
            case "GPL-1.0+":
                return "GPL-1.0+";
            case "GNU General Public License v2.0 only":
            case "GPL-2.0":
                return "GPL-2.0";
            case "GNU General Public License v2.0 or later":
            case "GPL-2.0+":
                return "GPL-2.0+";
            case "GNU General Public License v3.0 only":
            case "GPL-3.0":
                return "GPL-3.0";
            case "GNU General Public License v3.0 or later":
            case "GPL-3.0+":
                return "GPL-3.0+";
            case "GNU General Public License v2.0 w/Classpath exception":
            case "GPL-2.0-with-classpath-exception":
                return "GPL-2.0-with-classpath-exception";
            case "GNU Library General Public License v2 only":
            case "LGPL-2.0":
                return "LGPL-2.0";
            case "GNU Library General Public License v2 or later":
            case "LGPL-2.0+":
                return "LGPL-2.0+";
            case "GNU Library General Public License v2.1 only":
            case "GNU Lesser General Public License":
            case "LGPL-2.1":
                return "LGPL-2.1";
            case "GNU Library General Public License v2.1 or later":
            case "LGPL-2.1+":
                return "LGPL-2.1+";
            case "GNU Library General Public License v3.0 only":
            case "LGPL-3.0":
                return "LGPL-3.0";
            case "GNU Library General Public License v3.0 or later":
            case "LGPL-3.0+":
                return "LGPL-3.0+";
            case "Eclipse Public License - v 1.0":
            case "Eclipse Public License 1.0":
            case "EPL-1.0":
                return "EPL-1.0";
            case "Eclipse Public License - v 2.0":
            case "Eclipse Public License 2.0":
            case "EPL-2.0":
                return "EPL-2.0";
            default:
                return "name=" + license.name + ", url=" + license.url;
        }
    }
}
