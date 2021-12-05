package jbuild.maven;

import jbuild.artifact.Artifact;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import static java.util.stream.Collectors.toList;
import static jbuild.maven.MavenUtils.resolveProperty;
import static jbuild.util.XmlUtils.childNamed;
import static jbuild.util.XmlUtils.childrenNamed;
import static jbuild.util.XmlUtils.textOf;

public final class MavenPom {

    private final Element project;

    // only resolve properties if necessary
    private Map<String, String> properties;

    public MavenPom(Document doc) {
        this.project = childNamed("project", doc).orElseThrow(() ->
                new IllegalArgumentException("Not a POM XML document"));
    }

    public Optional<Artifact> getParent() {
        return childNamed("parent", project)
                .map(a -> MavenPom.toDependency(a, getProperties()))
                .map(d -> d.artifact);
    }

    public List<Dependency> getDependencies() {
        var depsNode = childNamed("dependencies", project);
        if (depsNode.isEmpty()) {
            return List.of();
        }
        var deps = childrenNamed("dependency", depsNode.get());
        var props = getProperties();
        return deps.stream()
                .map(dep -> MavenPom.toDependency(dep, props))
                .collect(toList());
    }

    public Artifact getCoordinates() {
        var artifact = toDependency(project, getProperties()).artifact;
        if (artifact.groupId.isBlank() || artifact.version.isBlank()) {
            return getParent().map(artifact::mergeWith).orElse(artifact);
        }
        return artifact;
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

    private static Dependency toDependency(Element element, Map<String, String> properties) {
        BiFunction<String, String, String> resolveProp = (name, defaultValue) ->
                resolveProperty(textOf(childNamed(name, element), defaultValue), properties);

        var groupId = resolveProp.apply("groupId", "");
        var artifactId = resolveProp.apply("artifactId", "");
        var version = resolveProp.apply("version", "");
        var scope = resolveProp.apply("scope", "compile");

        return new Dependency(new Artifact(groupId, artifactId, version),
                Scope.valueOf(scope.toUpperCase(Locale.ROOT)));
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

}
