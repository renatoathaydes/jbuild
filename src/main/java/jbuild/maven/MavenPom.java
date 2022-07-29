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
import static jbuild.maven.MavenUtils.isFullyResolved;
import static jbuild.util.CollectionUtils.mapValues;
import static jbuild.util.CollectionUtils.union;
import static jbuild.util.TextUtils.firstNonBlank;
import static jbuild.util.XmlUtils.childNamed;
import static jbuild.util.XmlUtils.childrenNamed;
import static jbuild.util.XmlUtils.textOf;

/**
 * Representation of a Maven POM.
 * <p>
 * Regardless of which constructor is used, this is an immutable object. All fields are populated eagerly.
 */
public final class MavenPom {

    /**
     * Strategy to use when "merging" two POMs.
     */
    private enum MergeMode {
        PARENT, IMPORT
    }

    private final MavenPom parentPom;
    private final Artifact parentArtifact;
    private final Artifact coordinates;
    private final String packaging;
    private final Map<String, String> properties;
    private final Map<ArtifactKey, NonEmptyCollection<Dependency>> dependencyManagement;
    private final Set<Dependency> dependencies;
    private final Set<License> licenses;

    private MavenPom(Element project, MavenPom parentPom) {
        // hide the non-populated properties in this block to avoid mistakes using it
        {
            var properties = resolveProperties(project, parentPom);

            this.parentPom = parentPom;
            this.parentArtifact = resolveParentArtifact(project, properties);
            this.coordinates = resolveCoordinates(project, properties, parentArtifact);
            this.properties = populateProjectPropertiesWith(coordinates, parentArtifact, properties);
        }
        this.packaging = resolveProperty(properties, childNamed("packaging", project), "jar");
        this.dependencyManagement = resolveDependencyManagement(project, properties, parentPom);
        this.dependencies = resolveDependencies(project, dependencyManagement, properties, parentPom);
        this.licenses = resolveLicenses(project, properties, parentPom);
    }

    private MavenPom(MavenPom pom, MavenPom other, MergeMode mode) {
        switch (mode) {
            case PARENT:
                this.parentPom = other;
                this.properties = union(other.properties, pom.properties);
                this.parentArtifact = resolveArtifact(other.coordinates, properties);
                this.coordinates = resolveArtifact(pom.coordinates, properties);
                this.packaging = resolveProperty(properties, pom.packaging, "jar");
                this.dependencyManagement = union(other.dependencyManagement,
                        mapValues(pom.dependencyManagement, deps ->
                                deps.map(dep -> refineDependency(dep, properties, other.dependencyManagement))),
                        NonEmptyCollection::of);
                this.dependencies = union(other.dependencies, pom.dependencies)
                        .stream().map(dep -> refineDependency(dep, properties, dependencyManagement))
                        .collect(toSet());
                this.licenses = union(other.licenses, pom.licenses)
                        .stream().map(license -> refineLicense(license, properties))
                        .collect(toSet());
                break;
            case IMPORT:
            default:
                this.parentPom = pom.parentPom;
                this.properties = pom.properties;
                this.parentArtifact = pom.parentArtifact;
                this.coordinates = pom.coordinates;
                this.packaging = pom.packaging;
                this.dependencyManagement = union(other.dependencyManagement,
                        mapValues(pom.dependencyManagement, deps ->
                                deps.map(dep -> refineDependency(dep, properties, other.dependencyManagement))),
                        NonEmptyCollection::of);
                this.dependencies = pom.dependencies
                        .stream().map(dep -> refineDependency(dep, properties, dependencyManagement))
                        .collect(toSet());
                this.licenses = pom.licenses
                        .stream().map(license -> refineLicense(license, properties))
                        .collect(toSet());
        }
    }

    /**
     * Create a POM from an XML {@link Document}.
     *
     * @param doc XML document
     */
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

    /**
     * @return the parent POM.
     */
    public Optional<MavenPom> getParentPom() {
        return Optional.ofNullable(parentPom);
    }

    /**
     * @return the parent artifact declared by this POM.
     */
    public Optional<Artifact> getParentArtifact() {
        return Optional.ofNullable(parentArtifact);
    }

    /**
     * @return the artifact coordinates of this POM.
     */
    public Artifact getArtifact() {
        return coordinates;
    }

    /**
     * @return the packaging of this POM.
     */
    public String getPackaging() {
        return packaging;
    }

    /**
     * @return the dependency-management section of this POM.
     */
    public Map<ArtifactKey, NonEmptyCollection<Dependency>> getDependencyManagement() {
        return dependencyManagement;
    }

    /**
     * @return the dependencies section of this POM.
     */
    public Set<Dependency> getDependencies() {
        return dependencies;
    }

    /**
     * Filter the dependencies of this POM that are included in the given scopes,
     * optionally including optional dependencies.
     *
     * @param scopes           to include
     * @param includeOptionals whether to include optional dependencies
     * @return dependencies matching the given parameters
     */
    public Set<Dependency> getDependencies(EnumSet<Scope> scopes, boolean includeOptionals) {
        if (scopes.size() == Scope.values().length && includeOptionals) {
            return dependencies;
        }

        return dependencies.stream()
                .filter(dep -> (scopes.contains(dep.scope) &&
                        (includeOptionals || !dep.optional)))
                .collect(toSet());
    }

    /**
     * @return the licenses declared in this POM.
     */
    public Set<License> getLicenses() {
        return licenses;
    }

    /**
     * @return the properties of this POM.
     */
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

    private static Set<License> resolveLicenses(
            Element project,
            Map<String, String> properties,
            MavenPom parentPom) {
        var licenses = resolveLicenses(project, properties);
        return union(parentPom == null ? Set.of() : parentPom.getLicenses(), licenses);
    }

    private static Set<License> resolveLicenses(
            Element project,
            Map<String, String> properties) {
        return childNamed("licenses", project).map(licenses ->
                childrenNamed("license", licenses).stream()
                        .map(license -> toLicense(license, properties))
                        .collect(toSet())
        ).orElse(Set.of());

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

    private static License toLicense(Element element,
                                     Map<String, String> properties) {
        var name = resolveProperty(properties, childNamed("name", element), "<unspecified>");
        var url = resolveProperty(properties, childNamed("url", element), "<unspecified>");
        return new License(name, url);
    }

    private static License refineLicense(License license,
                                         Map<String, String> properties) {
        if (isFullyResolved(license)) {
            return license;
        }
        var name = resolveProperty(properties, license.name, license.name);
        var url = resolveProperty(properties, license.url, license.url);
        return new License(name, url);
    }

    private static Dependency toDependency(Element element,
                                           Map<String, String> properties,
                                           Map<ArtifactKey, NonEmptyCollection<Dependency>> dependencyManagement) {
        var groupId = resolveProperty(properties, childNamed("groupId", element), "");
        var artifactId = resolveProperty(properties, childNamed("artifactId", element), "");
        var type = resolveProperty(properties, childNamed("type", element), "");

        var artifactKey = ArtifactKey.of(groupId, artifactId, DependencyType.fromString(type));

        var scope = Optional.ofNullable(
                resolvePropertyScope(properties, childNamed("scope", element), () ->
                        scopeFrom(dependencyManagement.get(artifactKey))));
        var version = resolveProperty(properties, childNamed("version", element),
                () -> defaultVersionOrFrom(scope.orElse(null),
                        dependencyManagement.get(artifactKey)));
        var optional = resolveProperty(properties, childNamed("optional", element), "false");
        var exclusions = resolveDependencyExclusions(element, properties);
        var classifier = resolveProperty(properties, childNamed("classifier", element), "");

        return new Dependency(new Artifact(groupId, artifactId, version, "", classifier),
                scope.orElse(Scope.COMPILE), optional, exclusions, type, scope.isPresent());
    }

    private static Dependency refineDependency(Dependency dependency,
                                               Map<String, String> properties,
                                               Map<ArtifactKey, NonEmptyCollection<Dependency>> dependencyManagement) {
        var groupId = resolveProperty(properties,
                dependency.artifact.groupId, dependency.artifact.groupId);
        var artifactId = resolveProperty(properties,
                dependency.artifact.artifactId, dependency.artifact.artifactId);

        var managedDeps = dependencyManagement.get(ArtifactKey.of(groupId, artifactId, dependency.type));

        var scope = Optional.ofNullable(dependency.explicitScope
                ? dependency.scope
                : scopeFrom(managedDeps));
        var version = resolveProperty(properties, dependency.artifact.version,
                () -> defaultVersionOrFrom(scope.orElse(null), managedDeps));
        var optional = resolveProperty(properties, dependency.optionalString,
                () -> managedDeps == null ? "false" : managedDeps.first.optionalString);
        var exclusions = refineExclusions(union(dependency.exclusions,
                managedDeps == null ? Set.of() : managedDeps.first.exclusions), properties);

        var type = Optional.ofNullable(dependency.explicitType
                ? dependency.type
                : typeFrom(managedDeps));

        return new Dependency(
                new Artifact(groupId, artifactId, version, dependency.artifact.extension, dependency.artifact.classifier),
                scope.orElse(Scope.COMPILE), optional, exclusions,
                type.map(DependencyType::string).orElse(""), scope.isPresent());
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

    private static Scope scopeFrom(NonEmptyCollection<Dependency> dependencies) {
        if (dependencies == null) return null;
        var scope = dependencies.first.scope;
        for (var dep : dependencies) {
            if (dep.explicitScope) {
                scope = dep.scope;
                break;
            }
        }
        return scope;
    }

    private static DependencyType typeFrom(NonEmptyCollection<Dependency> dependencies) {
        if (dependencies == null) return null;
        return dependencies.first.type;
    }

    private static Map<String, String> resolveProperties(Element project, MavenPom parentPom) {
        return childNamed("properties", project)
                .map(p -> {
                    var children = p.getChildNodes();
                    Map<String, String> parentProperties = parentPom == null ? Map.of() : parentPom.properties;

                    // "12 +" because we'll add about 10 built-in properties later
                    Map<String, String> result = new LinkedHashMap<>(12 +
                            children.getLength() + parentProperties.size());

                    result.putAll(parentProperties);

                    for (int i = 0; i < children.getLength(); i++) {
                        var child = children.item(i);
                        if (child instanceof Element) {
                            var elem = (Element) child;
                            result.put(elem.getTagName(), elem.getTextContent().trim());
                        }
                    }

                    return result;
                }).orElseGet(() -> new LinkedHashMap<>(12));
    }

    private static Map<String, String> populateProjectPropertiesWith(Artifact coordinates,
                                                                     Artifact parentArtifact,
                                                                     Map<String, String> properties) {
        properties.put("project.groupId", coordinates.groupId);
        properties.put("pom.groupId", coordinates.groupId);
        properties.put("project.artifactId", coordinates.artifactId);
        properties.put("pom.artifactId", coordinates.artifactId);
        properties.put("project.version", coordinates.version);
        properties.put("pom.version", coordinates.version);
        if (parentArtifact != null) {
            properties.put("project.parent.groupId", parentArtifact.groupId);
            properties.put("pom.parent.groupId", parentArtifact.groupId);
            properties.put("project.parent.artifactId", parentArtifact.artifactId);
            properties.put("pom.parent.artifactId", parentArtifact.artifactId);
            properties.put("project.parent.version", parentArtifact.version);
            properties.put("pom.parent.version", parentArtifact.version);
        }
        return unmodifiableMap(properties);
    }

}
