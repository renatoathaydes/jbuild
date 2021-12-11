package jbuild.maven;

import jbuild.artifact.Artifact;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static jbuild.util.CollectionUtils.union;
import static jbuild.util.TextUtils.firstNonBlank;
import static jbuild.util.XmlUtils.childNamed;
import static jbuild.util.XmlUtils.childrenNamed;
import static jbuild.util.XmlUtils.textOf;

public final class MavenPom {

    private final Artifact parentArtifact;
    private final Artifact coordinates;
    private final Map<String, String> properties;
    private final Map<ArtifactKey, Dependency> dependencyManagement;
    private final Set<Dependency> dependencies;

    private MavenPom(Element project, MavenPom parentPom) {
        this.properties = resolveProperties(project);
        this.parentArtifact = resolveParentArtifact(project, properties);
        this.coordinates = resolveCoordinates(project, properties, parentArtifact);
        this.dependencyManagement = resolveDependencyManagement(project, properties, parentPom);
        this.dependencies = resolveDependencies(project, dependencyManagement, properties, parentPom);
    }

    private MavenPom(MavenPom pom, MavenPom parentPom) {
        this.properties = union(pom.properties, parentPom.properties);
        this.parentArtifact = parentPom.coordinates;
        this.coordinates = pom.coordinates;
        this.dependencyManagement = union(parentPom.dependencyManagement, pom.dependencyManagement);
        this.dependencies = union(parentPom.dependencies, pom.dependencies)
                .stream().map(dep -> refineDependency(dep, dependencyManagement))
                .collect(toSet());
    }

    public MavenPom(Document doc) {
        this(childNamed("project", doc).orElseThrow(() ->
                        new IllegalArgumentException("Not a POM XML document")),
                null);
    }

    public MavenPom withParent(MavenPom parentPom) {
        return new MavenPom(this, parentPom);
    }

    public Optional<Artifact> getParentArtifact() {
        return Optional.ofNullable(parentArtifact);
    }

    public Artifact getCoordinates() {
        return coordinates;
    }

    public Map<ArtifactKey, Dependency> getDependencyManagement() {
        return dependencyManagement;
    }

    public Set<Dependency> getDependencies() {
        return dependencies;
    }

    public Set<Dependency> getDependencies(Scope scope) {
        if (scope == null) {
            return dependencies;
        }
        return dependencies.stream()
                .filter(dep -> scope.includes(dep.scope))
                .collect(toSet());
    }

    @Override
    public String toString() {
        return "MavenPom{" +
                getCoordinates() +
                ", dependencies=" + getDependencies() +
                '}';
    }

    private static Map<ArtifactKey, Dependency> resolveDependencyManagement(Element project,
                                                                            Map<String, String> properties,
                                                                            MavenPom parentPom) {
        var deps = childNamed("dependencyManagement", project)
                .map(dep -> resolveDependencies(dep, properties, Map.of())
                        .stream().collect(toMap(ArtifactKey::of, identity())))
                .orElse(Map.of());

        return union(parentPom == null ? Map.of() : parentPom.getDependencyManagement(), deps);
    }

    private static Set<Dependency> resolveDependencies(
            Element project,
            Map<ArtifactKey, Dependency> dependencyManagement,
            Map<String, String> properties,
            MavenPom parentPom) {
        var deps = resolveDependencies(project, properties, dependencyManagement);
        return union(parentPom == null ? Set.of() : parentPom.getDependencies(), deps);
    }

    private static Set<Dependency> resolveDependencies(Element parentElement,
                                                       Map<String, String> properties,
                                                       Map<ArtifactKey, Dependency> dependencyManagement) {
        var depsNode = childNamed("dependencies", parentElement);
        if (depsNode.isEmpty()) {
            return Set.of();
        }

        var deps = childrenNamed("dependency", depsNode.get());

        return deps.stream()
                .map(dep -> toDependency(dep, properties, dependencyManagement))
                .collect(toSet());
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
                                           Map<ArtifactKey, Dependency> dependencyManagement) {
        var groupId = resolveProperty(properties, childNamed("groupId", element), () -> "");
        var artifactId = resolveProperty(properties, childNamed("artifactId", element), () -> "");
        var version = resolveProperty(properties, childNamed("version", element),
                () -> defaultVersionOrFrom(dependencyManagement.get(ArtifactKey.of(groupId, artifactId))));
        var scope = resolvePropertyScope(properties, childNamed("scope", element), () ->
                defaultScopeOrFrom(dependencyManagement.get(ArtifactKey.of(groupId, artifactId))));

        return new Dependency(new Artifact(groupId, artifactId, version), scope);
    }

    private static Dependency refineDependency(Dependency dependency,
                                               Map<ArtifactKey, Dependency> dependencyManagement) {
        var groupId = dependency.artifact.groupId;
        var artifactId = dependency.artifact.artifactId;
        var version = firstNonBlank(dependency.artifact.version,
                () -> defaultVersionOrFrom(dependencyManagement.get(ArtifactKey.of(groupId, artifactId))));
        var scope = dependency.scope != null
                ? dependency.scope
                : defaultScopeOrFrom(dependencyManagement.get(ArtifactKey.of(groupId, artifactId)));

        return new Dependency(new Artifact(groupId, artifactId, version), scope);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static String resolveProperty(Map<String, String> properties,
                                          Optional<? extends Node> element,
                                          Supplier<String> defaultValue) {
        return MavenUtils.resolveProperty(textOf(element, defaultValue), properties);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static Scope resolvePropertyScope(Map<String, String> properties,
                                              Optional<? extends Node> element,
                                              Supplier<Scope> defaultValue) {
        var scopeText = resolveProperty(properties, element, () -> "");
        return scopeText.isBlank()
                ? defaultValue.get()
                : Scope.valueOf(scopeText.toUpperCase(Locale.ROOT));
    }

    private static String defaultVersionOrFrom(Dependency dependency) {
        if (dependency == null) return "";
        return dependency.artifact.version;
    }

    private static Scope defaultScopeOrFrom(Dependency dependency) {
        if (dependency == null) return Scope.COMPILE;
        return dependency.scope;
    }

    private static Map<String, String> resolveProperties(Element project) {
        return childNamed("properties", project)
                .map(p -> {
                    var children = p.getChildNodes();
                    Map<String, String> result = new LinkedHashMap<>(children.getLength());
                    for (int i = 0; i < children.getLength(); i++) {
                        var child = children.item(i);
                        if (child instanceof Element) {
                            var elem = (Element) child;
                            result.put(elem.getTagName(), elem.getTextContent());
                        }
                    }
                    return result;
                }).orElse(Map.of());
    }

}
