package jbuild.cli;

import jbuild.artifact.Artifact;
import jbuild.log.JBuildLog;
import jbuild.maven.ArtifactKey;
import jbuild.maven.Dependency;
import jbuild.maven.DependencyTree;
import jbuild.maven.DependencyType;
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
import java.util.TreeSet;
import java.util.concurrent.CompletionStage;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static jbuild.maven.Scope.expandScopes;
import static jbuild.util.AsyncUtils.runAsync;
import static jbuild.util.CollectionUtils.sorted;

final class DependencyTreeLogger {

    private static final String INDENT = "    ";

    private final JBuildLog log;
    private final DepsOptions options;

    DependencyTreeLogger(JBuildLog log, DepsOptions options) {
        this.log = log;
        this.options = options;
    }

    public CompletionStage<Void> log(DependencyTree tree) {
        return runAsync(() -> logTree(tree)).whenComplete((ok, err) -> {
            if (err != null) log.print(err);
        });
    }

    private void logTree(DependencyTree tree) {
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

        var deps = tree.getDependencies(expandScopes(options.scopes), options.optional);

        if (deps.isEmpty()) {
            log.println("  * no dependencies");
        } else {
            logDependencyTreeAndLicenses(tree, deps);
        }
        if (options.showExtra) {
            logExtra(tree);
        }
    }

    private void logDependencyTreeAndLicenses(DependencyTree tree, Set<Dependency> deps) {
        var groupedDeps = deps.stream()
                .collect(groupingBy(dep -> dep.scope));

        var allLicenses = options.licenses && options.transitive ? new HashSet<License>() : null;

        for (Scope scope : expandScopes(options.scopes)) {
            var scopeDeps = groupedDeps.get(scope);
            if (scopeDeps != null && !scopeDeps.isEmpty()) {
                log.println("  - scope " + scope);
                int dependencyCount;
                if (options.transitive) {
                    var chain = new DependencyChain(log);
                    logTree(chain, tree.dependencies, scopeDeps, allLicenses, INDENT, scope);
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
            log.println(displayDependency(INDENT, child, null));
        }
    }

    private void logTree(DependencyChain chain,
                         List<DependencyTree> dependencies,
                         Collection<Dependency> scopeDeps,
                         Set<License> allLicenses,
                         String indent,
                         Scope scope) {
        var childByKey = dependencies.stream()
                .collect(toMap(c -> ArtifactKey.of(c.root.artifact),
                        NonEmptyCollection::of, NonEmptyCollection::of));

        for (var dep : sorted(scopeDeps, comparing(dep -> dep.artifact.getCoordinates()))) {
            var isNew = !chain.contains(dep);

            if (isNew) {
                var nextBranch = childByKey.get(ArtifactKey.of(dep));
                if (nextBranch == null) {
                    // missing information about dependency
                    log.print(() -> displayDependency(indent, dep, null));
                    log.println(" (X)");
                } else {
                    log.print(() -> displayDependency(indent, dep, nextBranch.first.displayVersion()));
                    var pomLicenses = nextBranch.first.root.pom.getLicenses();
                    if (options.licenses && !pomLicenses.isEmpty()) {
                        allLicenses.addAll(pomLicenses);
                        log.println(() -> displayLicenses(pomLicenses));
                    } else {
                        log.println("");
                    }
                    for (var next : nextBranch) {
                        chain.add(next);
                        var nextDeps = next.getDependencies(scope.transitiveScopes(), options.optional);
                        logTree(chain, next.dependencies, nextDeps, allLicenses, indent + INDENT, scope);
                        chain.remove(next);
                    }
                }
            } else {
                // dependency sub-tree already displayed
                log.print(() -> displayDependency(indent, dep, null));
                log.println(" (-)");
            }
        }
    }

    private void logLicenses(Set<License> allLicenses) {
        log.println("All licenses listed (see https://spdx.org/licenses/ for more information):");
        allLicenses.stream()
                .map(DependencyTreeLogger::licenseString)
                .collect(toCollection(TreeSet::new))
                .forEach(license -> log.println("  * " + license));
    }

    private static String displayLicenses(Set<License> licenses) {
        return licenses.stream()
                .map(lic -> '{' + licenseString(lic) + '}')
                .collect(joining(", ", " [", "]"));
    }

    private static String displayDependency(String indent, Dependency dep, String displayVersion) {
        var version = displayVersion == null ? dep.artifact.version : displayVersion;
        return indent + "* " + ArtifactKey.of(dep.artifact).getCoordinates() + ':' + version + ' ' +
                dependencyExtra(dep, false);
    }

    private static String dependencyExtra(Dependency dep) {
        return dependencyExtra(dep, true);
    }

    private static String dependencyExtra(Dependency dep, boolean includeVersion) {
        var classifier = dep.getClassifier();
        var type = dep.type;
        return (includeVersion ? ":" + dep.artifact.version + " " : "") +
                "[" + dep.scope + "]" +
                (dep.optional ? "[optional]" : "") +
                (type == DependencyType.JAR ? "" : "(" + type.string() + ")") +
                (classifier.isBlank() ? "" : "{" + classifier + "}") +
                (dep.exclusions.isEmpty() ? "" : dep.exclusions.stream()
                        .map(ArtifactKey::getCoordinates)
                        .collect(joining(", ", ":exclusions:[", "]")));
    }

    private void logExtra(DependencyTree tree) {
        var pom = tree.root.pom;
        log.println("Extra information about " + tree.root.artifact.getCoordinates() + ":");

        var parent = pom.getParentPom().orElse(null);
        if (parent == null) {
            log.println("  - No parent POM.");
        } else {
            log.println("  - Parent POMs:");
        }
        while (parent != null) {
            log.println("    * " + parent.getArtifact().getCoordinates());
            log.println("      - Dependencies:");
            if (parent.getDependencies().isEmpty()) {
                log.println("        * no dependencies");
            } else {
                for (var dep : sorted(parent.getDependencies(), comparing(d -> d.artifact.getCoordinates()))) {
                    log.println(displayDependency("        ", dep, dep.artifact.version));
                }
                var dependencyCount = parent.getDependencies().size();
                log.println(() -> "      " + dependencyCount + " " +
                        " dependenc" + (dependencyCount == 1 ? "y" : "ies") + " listed");
            }
            parent = parent.getParentPom().orElse(null);
        }
        log.println("  - Dependency Management:");
        if (pom.getDependencyManagement().isEmpty()) {
            log.println("    <empty>");
        } else {
            for (var dep : sorted(pom.getDependencyManagement().entrySet(),
                    comparing(dep -> dep.getKey().getCoordinates()))) {
                log.println(displayEntry(dep));
            }
            var dependencyCount = pom.getDependencyManagement().size();
            log.println(() -> "  " + dependencyCount + " " +
                    " dependenc" + (dependencyCount == 1 ? "y" : "ies") + " listed");
        }
    }

    private String displayEntry(Map.Entry<ArtifactKey, NonEmptyCollection<Dependency>> entry) {
        var deps = entry.getValue().stream().collect(toSet());
        if (deps.size() == 1) {
            return displayDependency(INDENT, entry.getValue().first, null);
        }
        return INDENT + "* " + entry.getKey().getCoordinates() +
                entry.getValue().stream()
                        .map(DependencyTreeLogger::dependencyExtra)
                        .collect(joining(", ", "{", "}"));
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
            case "The Apache Software License, Version 2.0":
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
