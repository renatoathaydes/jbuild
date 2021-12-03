package jbuild.maven;

import jbuild.artifact.Artifact;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.List;
import java.util.Locale;

import static java.util.stream.Collectors.toList;
import static jbuild.util.XmlUtils.childNamed;
import static jbuild.util.XmlUtils.childrenNamed;
import static jbuild.util.XmlUtils.textOf;

public final class MavenPom {

    private final Element project;

    public MavenPom(Document doc) {
        this.project = childNamed("project", doc).orElseThrow(() ->
                new IllegalArgumentException("Not a POM XML document"));
    }

    public List<Dependency> getDependencies() {
        var depsNode = childNamed("dependencies", project);
        if (depsNode.isEmpty()) {
            return List.of();
        }
        var deps = childrenNamed("dependency", depsNode.get());
        return deps.stream()
                .map(MavenPom::toDependency)
                .collect(toList());
    }

    public Artifact getCoordinates() {
        return toDependency(project).getArtifact();
    }

    @Override
    public String toString() {
        return "MavenPom{" +
                getCoordinates() +
                ", dependencies=" + getDependencies() +
                '}';
    }

    private static Dependency toDependency(Element element) {
        var groupId = textOf(childNamed("groupId", element));
        var artifactId = textOf(childNamed("artifactId", element));
        var version = textOf(childNamed("version", element));
        var scope = textOf(childNamed("scope", element), "compile");
        return new Dependency(new Artifact(groupId, artifactId, version),
                Scope.valueOf(scope.toUpperCase(Locale.ROOT)));
    }

}
