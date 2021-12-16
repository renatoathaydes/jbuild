package jbuild.maven;

import jbuild.artifact.Artifact;

import java.util.Set;

public class MavenHelper {

    public static MavenPom readPom(String resourcePath) throws Exception {
        try (var stream = MavenUtilsTest.class.getResourceAsStream(resourcePath)) {
            return MavenUtils.parsePom(stream);
        }
    }

    public static Dependency dep(String groupId,
                                 String artifactId,
                                 String version,
                                 Scope scope,
                                 boolean optional,
                                 Set<ArtifactKey> exclusions) {
        return new Dependency(new Artifact(groupId, artifactId, version), scope, optional, exclusions, true);
    }

    public static Dependency dep(String groupId, String artifactId, String version, Scope scope, boolean optional) {
        return new Dependency(new Artifact(groupId, artifactId, version), scope, optional, Set.of(), true);
    }

    public static Dependency dep(String groupId, String artifactId, String version, Scope scope) {
        return new Dependency(new Artifact(groupId, artifactId, version), scope, false, Set.of(), true);
    }

    public static Dependency dep(String groupId, String artifactId, String version) {
        return new Dependency(new Artifact(groupId, artifactId, version));
    }

}
