package jbuild.maven;

import jbuild.artifact.Artifact;
import jbuild.util.NonEmptyCollection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.Collections.unmodifiableMap;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static jbuild.util.CollectionUtils.union;
import static jbuild.util.TextUtils.firstNonBlank;
import static jbuild.util.XmlUtils.childNamed;
import static jbuild.util.XmlUtils.childrenNamed;
import static jbuild.util.XmlUtils.textOf;

public final class MavenPom {

    private enum MergeMode {
        PARENT, IMPORT
    }

    private final Artifact parentArtifact;
    private final Artifact coordinates;
    private final Map<String, String> properties;
    private final Map<ArtifactKey, NonEmptyCollection<Dependency>> dependencyManagement;
    private final Set<Dependency> dependencies;

    private MavenPom(Element project, MavenPom parentPom) {
        var properties = resolveProperties(project, parentPom);
        this.parentArtifact = resolveParentArtifact(project, properties);
        this.coordinates = resolveCoordinates(project, properties, parentArtifact);
        this.properties = populateProjectPropertiesWith(coordinates, properties);
        this.dependencyManagement = resolveDependencyManagement(project, properties, parentPom);
        this.dependencies = resolveDependencies(project, dependencyManagement, properties, parentPom);
    }

    private MavenPom(MavenPom pom, MavenPom other, MergeMode mode) {
        switch (mode) {
            case PARENT:
                this.properties = union(other.properties, pom.properties);
                this.parentArtifact = resolveArtifact(other.coordinates, properties);
                this.coordinates = resolveArtifact(pom.coordinates, properties);
                this.dependencyManagement = union(other.dependencyManagement,
                        pom.dependencyManagement, NonEmptyCollection::of);
                this.dependencies = union(other.dependencies, pom.dependencies)
                        .stream().map(dep -> refineDependency(dep, properties, dependencyManagement))
                        .collect(toSet());
                break;
            case IMPORT:
            default:
                this.properties = pom.properties;
                this.parentArtifact = pom.parentArtifact;
                this.coordinates = pom.coordinates;
                this.dependencyManagement = union(pom.dependencyManagement,
                        other.dependencyManagement, NonEmptyCollection::of);
                this.dependencies = pom.dependencies
                        .stream().map(dep -> refineDependency(dep, properties, dependencyManagement))
                        .collect(toSet());
        }
    }

    public MavenPom(Document doc) {
        this(childNamed("project", doc).orElseThrow(() ->
                        new IllegalArgumentException("Not a POM XML document")),
                null);
    }

    /**
     * Create a new {@link MavenPom} with the given parent POM as the parent of this POM.
     * <p>
     * This method does not check whether this POM actually declares the given parent.
     *
     * @param parentPom another POM to use as this POM's parent
     * @return this POM with the given parent POM
     */
    public MavenPom withParent(MavenPom parentPom) {
        return new MavenPom(this, parentPom, MergeMode.PARENT);
    }

    /**
     * Create a new {@link MavenPom} that imports the given POM (i.e. merging this POM that the given POM's
     * dependencyManagement section).
     * <p>
     * This method does not check whether this POM actually imports the given POM.
     *
     * @param importedPom another POM to import into this POM
     * @return this POM after importing the given POM
     */
    public MavenPom importing(MavenPom importedPom) {
        return new MavenPom(this, importedPom, MergeMode.IMPORT);
    }

    public Optional<Artifact> getParentArtifact() {
        return Optional.ofNullable(parentArtifact);
    }

    public Artifact getArtifact() {
        return coordinates;
    }

    public Map<ArtifactKey, NonEmptyCollection<Dependency>> getDependencyManagement() {
        return dependencyManagement;
    }

    public Set<Dependency> getDependencies() {
        return dependencies;
    }

    public Set<Dependency> getDependencies(EnumSet<Scope> scopes, boolean includeOptionals) {
        if (scopes.size() == Scope.values().length && includeOptionals) {
            return dependencies;
        }

        return dependencies.stream()
                .filter(dep -> (scopes.contains(dep.scope) &&
                        (includeOptionals || !dep.optional)))
                .collect(toSet());
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public String toString() {
        return "MavenPom{" +
                getArtifact() +
                ", dependencies=" + getDependencies() +
                ", dependencyManagement=" + getDependencyManagement() +
                '}';
    }

    private static Map<ArtifactKey, NonEmptyCollection<Dependency>> resolveDependencyManagement(
            Element project,
            Map<String, String> properties,
            MavenPom parentPom) {
        var deps = childNamed("dependencyManagement", project)
                .map(dep -> resolveDependencies(dep, properties, Map.of())
                        .stream().collect(toMap(ArtifactKey::of,
                                NonEmptyCollection::of, NonEmptyCollection::of)))
                .orElse(Map.of());

        return parentPom == null
                ? deps
                : union(parentPom.getDependencyManagement(), deps, NonEmptyCollection::of);
    }

    private static Set<Dependency> resolveDependencies(
            Element project,
            Map<ArtifactKey, NonEmptyCollection<Dependency>> dependencyManagement,
            Map<String, String> properties,
            MavenPom parentPom) {
        var deps = resolveDependencies(project, properties, dependencyManagement);
        return union(parentPom == null ? Set.of() : parentPom.getDependencies(), deps);
    }

    private static Set<Dependency> resolveDependencies(
            Element parentElement,
            Map<String, String> properties,
            Map<ArtifactKey, NonEmptyCollection<Dependency>> dependencyManagement) {
        var depsNode = childNamed("dependencies", parentElement);
        if (depsNode.isEmpty()) {
            return Set.of();
        }

        var deps = childrenNamed("dependency", depsNode.get());

        return deps.stream()
                .map(dep -> toDependency(dep, properties, dependencyManagement))
                .collect(toSet());
    }

    private static Set<ArtifactKey> resolveDependencyExclusions(Element dependencyElement,
                                                                Map<String, String> properties) {
        return childNamed("exclusions", dependencyElement)
                .map(exc -> childrenNamed("exclusion", exc).stream()
                        .map(exclusion -> toArtifactKey(exclusion, properties))
                        .collect(toSet()))
                .orElse(Set.of());
    }

    private static Artifact resolveParentArtifact(Element project, Map<String, String> properties) {
        return childNamed("parent", project)
                .map(a -> toDependency(a, properties, Map.of()))
                .map(d -> d.artifact)
                .orElse(null);
    }

    private static Artifact resolveCoordinates(Element project, Map<String, String> properties, Artifact parentArtifact) {
        var artifact = toDependency(project, properties, Map.of()).artifact;
        if ((artifact.groupId.isBlank() || artifact.version.isBlank()) && parentArtifact != null) {
            return artifact.mergeWith(parentArtifact);
        }
        return artifact;
    }

    private static Dependency toDependency(Element element,
                                           Map<String, String> properties,
                                           Map<ArtifactKey, NonEmptyCollection<Dependency>> dependencyManagement) {
        var groupId = resolveProperty(properties, childNamed("groupId", element), () -> "");
        var artifactId = resolveProperty(properties, childNamed("artifactId", element), () -> "");
        var scope = Optional.ofNullable(
                resolvePropertyScope(properties, childNamed("scope", element), () ->
                        defaultScopeOrFrom(dependencyManagement.get(ArtifactKey.of(groupId, artifactId)))));
        var version = resolveProperty(properties, childNamed("version", element),
                () -> defaultVersionOrFrom(scope.orElse(null),
                        dependencyManagement.get(ArtifactKey.of(groupId, artifactId))));
        var optional = resolveProperty(properties, childNamed("optional", element), () -> "false");
        var exclusions = resolveDependencyExclusions(element, properties);

        return new Dependency(new Artifact(groupId, artifactId, version),
                scope.orElse(Scope.COMPILE), optional, exclusions, scope.isPresent());
    }

    private static Dependency refineDependency(Dependency dependency,
                                               Map<String, String> properties,
                                               Map<ArtifactKey, NonEmptyCollection<Dependency>> dependencyManagement) {
        // TODO check if dependency is fully resolved to save the work of creating new objects if possible
        var groupId = resolveProperty(properties,
                dependency.artifact.groupId, dependency.artifact.groupId);
        var artifactId = resolveProperty(properties,
                dependency.artifact.artifactId, dependency.artifact.artifactId);
        var scope = Optional.ofNullable(dependency.explicitScope
                ? dependency.scope
                : defaultScopeOrFrom(dependencyManagement.get(ArtifactKey.of(groupId, artifactId))));
        var version = resolveProperty(properties, dependency.artifact.version,
                () -> defaultVersionOrFrom(scope.orElse(null),
                        dependencyManagement.get(ArtifactKey.of(groupId, artifactId))));
        var optional = resolveProperty(properties, dependency.optionalString, "false");
        var exclusions = refineExclusions(dependency.exclusions, properties);
        return new Dependency(new Artifact(groupId, artifactId, version),
                scope.orElse(Scope.COMPILE), optional, exclusions, scope.isPresent());
    }

    private static Set<ArtifactKey> refineExclusions(Set<ArtifactKey> exclusions,
                                                     Map<String, String> properties) {
        return exclusions.stream().map(exclusion -> {
            var groupId = resolveProperty(properties, exclusion.groupId, exclusion.groupId);
            var artifactId = resolveProperty(properties, exclusion.artifactId, exclusion.artifactId);
            return ArtifactKey.of(groupId, artifactId);
        }).collect(toSet());
    }

    private static ArtifactKey toArtifactKey(Element element,
                                             Map<String, String> properties) {
        var groupId = resolveProperty(properties, childNamed("groupId", element), () -> "");
        var artifactId = resolveProperty(properties, childNamed("artifactId", element), () -> "");
        return ArtifactKey.of(groupId, artifactId);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static String resolveProperty(Map<String, String> properties,
                                          Optional<? extends Node> element,
                                          Supplier<String> defaultValue) {
        return MavenUtils.resolveProperty(textOf(element, defaultValue), properties);
    }

    private static String resolveProperty(Map<String, String> properties,
                                          Optional<? extends Node> element,
                                          String defaultValue) {
        return MavenUtils.resolveProperty(textOf(element, defaultValue), properties);
    }

    private static String resolveProperty(Map<String, String> properties,
                                          String value,
                                          Supplier<String> defaultValue) {
        return MavenUtils.resolveProperty(firstNonBlank(value, defaultValue), properties);
    }

    private static String resolveProperty(Map<String, String> properties,
                                          String value,
                                          String defaultValue) {
        return MavenUtils.resolveProperty(firstNonBlank(value, defaultValue), properties);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static Scope resolvePropertyScope(Map<String, String> properties,
                                              Optional<? extends Node> element,
                                              Supplier<Scope> defaultValue) {
        var scopeText = resolveProperty(properties, element, "");
        return scopeText.isBlank()
                ? defaultValue.get()
                : Scope.valueOf(scopeText.toUpperCase(Locale.ROOT));
    }

    private static Artifact resolveArtifact(Artifact artifact,
                                            Map<String, String> properties) {
        return new Artifact(
                resolveProperty(properties, artifact.groupId, artifact.groupId),
                resolveProperty(properties, artifact.artifactId, artifact.artifactId),
                resolveProperty(properties, artifact.version, artifact.version));
    }

    private static String defaultVersionOrFrom(Scope scope,
                                               NonEmptyCollection<Dependency> dependencies) {
        if (dependencies == null) return "";
        for (var dependency : dependencies) {
            if (scope == null || scope == dependency.scope) {
                return dependency.artifact.version;
            }
        }
        // if the exact scope was not found, allow a scope that is included in the given scope
        if (scope != null) {
            for (var dependency : dependencies) {
                if (scope.includes(dependency.scope)) {
                    return dependency.artifact.version;
                }
            }
        }
        return "";
    }

    private static Scope defaultScopeOrFrom(NonEmptyCollection<Dependency> dependencies) {
        if (dependencies == null) return null;
        return dependencies.first.scope;
    }

    private static Map<String, String> resolveProperties(Element project, MavenPom parentPom) {
        return childNamed("properties", project)
                .map(p -> {
                    var children = p.getChildNodes();
                    Map<String, String> parentProperties = parentPom == null ? Map.of() : parentPom.properties;

                    // "1 +" because we'll add the "project.version" property later
                    Map<String, String> result = new LinkedHashMap<>(1 +
                            children.getLength() + parentProperties.size());

                    result.putAll(parentProperties);

                    for (int i = 0; i < children.getLength(); i++) {
                        var child = children.item(i);
                        if (child instanceof Element) {
                            var elem = (Element) child;
                            result.put(elem.getTagName(), elem.getTextContent());
                        }
                    }

                    return result;
                }).orElseGet(() -> new LinkedHashMap<>(1));
    }

    private static Map<String, String> populateProjectPropertiesWith(Artifact coordinates,
                                                                     Map<String, String> properties) {
        properties.put("project.groupId", coordinates.groupId);
        properties.put("project.artifactId", coordinates.artifactId);
        properties.put("project.version", coordinates.version);
        return unmodifiableMap(properties);
    }

}
