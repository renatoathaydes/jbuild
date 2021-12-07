package jbuild.maven;

import jbuild.artifact.Artifact;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Collectors.toUnmodifiableMap;
import static jbuild.util.CollectionUtils.union;
import static jbuild.util.XmlUtils.childNamed;
import static jbuild.util.XmlUtils.childrenNamed;
import static jbuild.util.XmlUtils.textOf;

public final class MavenPom {

    private final Element project;
    private final MavenPom parentPom;

    // only resolve properties if necessary
    private Map<String, String> properties;

    private MavenPom(Element project, MavenPom parentPom) {
        this.project = project;
        this.parentPom = parentPom;
    }

    public MavenPom(Document doc) {
        this(childNamed("project", doc).orElseThrow(() ->
                        new IllegalArgumentException("Not a POM XML document")),
                null);
    }

    public MavenPom withParent(MavenPom parentPom) {
        return new MavenPom(project, parentPom);
    }

    public Optional<Artifact> getParentArtifact() {
        return childNamed("parent", project)
                .map(a -> toDependency(a, getProperties(), Map.of()))
                .map(d -> d.artifact);
    }

    public Artifact getCoordinates() {
        var artifact = toDependency(project, getProperties(), Map.of()).artifact;
        if (artifact.groupId.isBlank() || artifact.version.isBlank()) {
            return getParentArtifact().map(artifact::mergeWith).orElse(artifact);
        }
        return artifact;
    }

    public Set<Dependency> getDependencyManagement() {
        var deps = childNamed("dependencyManagement", project)
                .map(dep -> getDependencies(dep, Map.of()))
                .orElse(Set.of());

        return union(parentPom == null ? Set.of() : parentPom.getDependencyManagement(), deps);
    }

    public Set<Dependency> getDependencies() {
        var deps = getDependencies(project, getDependencyManagementMap());
        return union(parentPom == null ? Set.of() : parentPom.getDependencies(), deps);
    }

    private Set<Dependency> getDependencies(Element parentElement,
                                            Map<ArtifactKey, Dependency> dependencyManagement) {
        var depsNode = childNamed("dependencies", parentElement);
        if (depsNode.isEmpty()) {
            return Set.of();
        }

        var deps = childrenNamed("dependency", depsNode.get());
        var props = getProperties();

        return deps.stream()
                .map(dep -> toDependency(dep, props, dependencyManagement))
                .collect(toSet());
    }

    private Map<ArtifactKey, Dependency> getDependencyManagementMap() {
        return getDependencyManagement().stream()
                .collect(toUnmodifiableMap(ArtifactKey::of, identity()));
    }

    private Map<String, String> getProperties() {
        if (properties == null) {
            properties = resolveProperties(project);
        }
        return properties;
    }

    @Override
    public String toString() {
        return "MavenPom{" +
                getCoordinates() +
                ", dependencies=" + getDependencies() +
                '}';
    }

    private static Dependency toDependency(Element element,
                                           Map<String, String> properties,
                                           Map<ArtifactKey, Dependency> dependencyManagement) {
        var groupId = resolveProperty(properties, childNamed("groupId", element), () -> "");
        var artifactId = resolveProperty(properties, childNamed("artifactId", element), () -> "");
        var version = resolveProperty(properties, childNamed("version", element),
                () -> defaultVersionOrFrom(dependencyManagement.get(new ArtifactKey(groupId, artifactId))));
        var scope = resolvePropertyScope(properties, childNamed("scope", element), () ->
                defaultScopeOrFrom(dependencyManagement.get(new ArtifactKey(groupId, artifactId))));

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
                    Map<String, String> result = new HashMap<>(children.getLength());
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

    private static final class ArtifactKey {
        private final String groupId;
        private final String artifactId;

        static ArtifactKey of(Dependency dependency) {
            var a = dependency.artifact;
            return new ArtifactKey(a.groupId, a.artifactId);
        }

        public ArtifactKey(String groupId, String artifactId) {
            this.groupId = groupId;
            this.artifactId = artifactId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ArtifactKey that = (ArtifactKey) o;

            return groupId.equals(that.groupId) &&
                    artifactId.equals(that.artifactId);
        }

        @Override
        public int hashCode() {
            int result = groupId.hashCode();
            result = 31 * result + artifactId.hashCode();
            return result;
        }
    }

}
