package jbuild.maven;

import jbuild.artifact.Artifact;
import jbuild.util.WritableXml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Set;

import static jbuild.util.TextUtils.firstNonBlank;

public final class Dependency implements WritableXml {

    public final Artifact artifact;
    public final Scope scope;
    public final boolean optional;
    public final Set<ArtifactKey> exclusions;
    public final DependencyType type;

    // keep the original String value so we can resolve it if needed
    final String optionalString;
    final boolean explicitScope;
    final boolean explicitType;

    Dependency(Artifact artifact,
               Scope scope,
               String optionalString,
               Set<ArtifactKey> exclusions,
               String type,
               boolean explicitScope) {
        this.scope = scope;
        this.optional = "true".equals(optionalString);
        this.optionalString = optionalString;
        this.exclusions = exclusions;
        this.explicitScope = explicitScope;

        DependencyType depType;
        if (!type.isBlank()) {
            depType = DependencyType.fromString(type);
            explicitType = true;
        } else if (!artifact.classifier.isBlank()) {
            depType = DependencyType.fromClassifier(artifact.classifier);
            explicitType = false;
        } else {
            depType = DependencyType.JAR;
            explicitType = false;
        }

        this.artifact = artifact.classifier.isBlank() && !depType.getClassifier().isBlank()
                ? artifact.withClassifier(depType.getClassifier())
                : artifact;

        this.type = depType;
    }

    public Dependency(Artifact artifact) {
        this(artifact, Scope.COMPILE, "", Set.of(), "", false);
    }

    public String getClassifier() {
        return firstNonBlank(artifact.classifier, type.getClassifier());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Dependency that = (Dependency) o;

        if (optional != that.optional) return false;
        if (scope != that.scope) return false;
        if (type != that.type) return false;
        if (!artifact.equals(that.artifact)) return false;
        return exclusions.equals(that.exclusions);
    }

    @Override
    public int hashCode() {
        int result = artifact.hashCode();
        result = 31 * result + scope.hashCode();
        result = 31 * result + (optional ? 1 : 0);
        result = 31 * result + exclusions.hashCode();
        result = 31 * result + type.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Dependency{" +
                "artifact=" + artifact +
                ", scope=" + scope +
                ", optional=" + optional +
                ", type=" + type.name() +
                ", exclusions=" + exclusions +
                '}';
    }

    @Override
    public void addTo(Element element, Document document) {
        var dep = document.createElement("dependency");
        artifact.addTo(dep, document, false);
        if (optional) {
            dep.appendChild(document.createElement("optional")).setTextContent("true");
        }
        if (scope != Scope.COMPILE) {
            dep.appendChild(document.createElement("scope")).setTextContent(scope.toString());
        }
        element.appendChild(dep);
    }
}
