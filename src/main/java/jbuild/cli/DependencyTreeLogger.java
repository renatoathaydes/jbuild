package jbuild.cli;

import jbuild.artifact.Artifact;
import jbuild.log.JBuildLog;
import jbuild.maven.ArtifactKey;
import jbuild.maven.Dependency;
import jbuild.maven.DependencyTree;
import jbuild.maven.License;
import jbuild.maven.Scope;
import jbuild.util.NonEmptyCollection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

        var allLicenses = options.licenses && options.transitive ? new HashSet<License>() : null;

        for (Scope scope : options.scopes) {
            var scopeDeps = groupedDeps.get(scope);
            if (scopeDeps != null && !scopeDeps.isEmpty()) {
                int dependencyCount;
                log.println("  - scope " + scope);
                if (options.transitive) {
                    var chain = new DependencyChain(log);
                    logTree(chain, tree.displayVersion(), scopeDeps, tree.dependencies, tree.root.pom.getLicenses(), allLicenses, INDENT, scope);
                    dependencyCount = chain.size();
                    chain.logConflicts();
                } else {
                    logChildren(scopeDeps);
                    dependencyCount = scopeDeps.size();
                }
                log.println(() -> "  " + dependencyCount + " " + scope +
                        " dependenc" + (dependencyCount == 1 ? "y" : "ies") + " listed");
            }
        }

        if (allLicenses != null && !allLicenses.isEmpty()) {
            logLicenses(allLicenses);
        }
    }

    private void logChildren(Collection<Dependency> children) {
        for (var child : sorted(children, comparing(dep -> dep.artifact.getCoordinates()))) {
            log.println(displayDependency(INDENT, child));
        }
    }

    private void logTree(DependencyChain chain,
                         String version,
                         Collection<Dependency> scopeDeps,
                         List<DependencyTree> children,
                         Set<License> pomLicenses,
                         Set<License> allLicenses,
                         String indent,
                         Scope scope) {
        var childByKey = children.stream()
                .collect(toMap(c -> ArtifactKey.of(c.root.artifact),
                        NonEmptyCollection::of, NonEmptyCollection::of));

        for (var dep : sorted(scopeDeps, comparing(dep -> dep.artifact.getCoordinates()))) {
            log.print(() -> displayDependency(indent, dep));

            var isNew = !chain.contains(dep);

            if (isNew) {
                var nextBranch = childByKey.get(ArtifactKey.of(dep));
                if (nextBranch == null) {
                    log.println(" (X)");
                } else {
                    if (options.licenses && !pomLicenses.isEmpty()) {
                        allLicenses.addAll(pomLicenses);
                        log.println(() -> displayLicenses(pomLicenses));
                    } else {
                        log.println("");
                    }
                    for (var next : nextBranch) {
                        chain.add(next);
                        var nextDeps = next.root.pom
                                .getDependencies(scope.transitiveScopes(), options.optional);
                        logTree(chain, next.displayVersion(), nextDeps, next.dependencies,
                                next.root.pom.getLicenses(), allLicenses, indent + INDENT, scope);
                        chain.remove(next);
                    }
                }
            } else {
                log.println(" (-)");
            }
        }
    }

    private void logLicenses(Set<License> allLicenses) {
        log.println("All licenses listed (see https://spdx.org/licenses/ for more information):");
        allLicenses.stream()
                .map(DependencyTreeLogger::licenseString)
                .sorted()
                .forEach(license -> log.println("  * " + license));
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
            case "Apache License, Version 1.0":
            case "Apache License 1.0":
            case "ASF 1.0":
            case "Apache 1":
            case "Apache 1.0":
            case "Apache-1.0":
                return "Apache-1.0";
            case "The Apache License, Version 1.1":
            case "Apache License, Version 1.1":
            case "Apache License 1.1":
            case "Apache 1.1":
            case "ASF 1.1":
            case "Apache-1.1":
                return "Apache-1.1";
            case "The Apache License, Version 2.0":
            case "Apache License, Version 2.0":
            case "Apache License 2.0":
            case "Apache 2":
            case "Apache 2.0":
            case "ASF 2.0":
            case "Apache-2.0":
                return "Apache-2.0";
            case "Do What The F*ck You Want To Public License":
            case "WTFPL":
                return "WTFPL";
            case "The New BSD License":
            case "New BSD License":
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
            case "Common Documentation License 1.0":
            case "CLD-1.0":
                return "CDL-1.0";
            case "Common Development and Distribution License (CDDL) v1.0":
            case "CDDL-1.0":
                return "CDDL-1.0";
            case "Common Development and Distribution License (CDDL) v1.1":
            case "CDDL-1.1":
                return "CDDL-1.1";
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

    private static final class VersionsEntry {
        final Map<String, List<String>> chainsByVersion = new HashMap<>(2);
    }

    private static final class DependencyChain {

        private final JBuildLog log;
        private int size;
        final List<Artifact> chain = new ArrayList<>();
        final Map<ArtifactKey, VersionsEntry> chainByArtifactKey = new HashMap<>();

        public DependencyChain(JBuildLog log) {
            this.log = log;
        }

        void add(DependencyTree node) {
            var artifact = node.root.artifact;
            chain.add(artifact);
            var byVersion = chainByArtifactKey.computeIfAbsent(
                    ArtifactKey.of(artifact),
                    (ignore) -> new VersionsEntry()
            ).chainsByVersion.computeIfAbsent(
                    artifact.version,
                    (ignore) -> new ArrayList<>(2)
            );
            // only count if artifact is unique
            if (byVersion.isEmpty()) size++;
            byVersion.add(chainString(chain));
        }

        void remove(DependencyTree node) {
            var removed = chain.remove(chain.size() - 1);
            if (!node.root.artifact.equals(removed)) {
                throw new IllegalStateException("Cannot remove node if not last in chain");
            }
        }

        int size() {
            return size;
        }

        private static String chainString(List<Artifact> chain) {
            return chain.stream()
                    .map(Artifact::getCoordinates)
                    .collect(joining(" -> "));
        }

        boolean contains(Dependency dependency) {
            var chain = chainByArtifactKey.get(ArtifactKey.of(dependency));
            return chain != null && chain.chainsByVersion.containsKey(dependency.artifact.version);
        }

        void logConflicts() {
            if (!log.isEnabled()) return;

            chainByArtifactKey.forEach((key, entry) -> {
                var byVersion = entry.chainsByVersion;
                if (byVersion.size() > 1) {
                    log.println("  The artifact " +
                            key.groupId + ":" + key.artifactId +
                            " is required with more than one version:");
                    byVersion.forEach((version, chains) -> {
                        for (String chain : chains) {
                            log.println("    * " + version + " (" + chain + ")");
                        }
                    });
                }
            });
        }
    }
}
