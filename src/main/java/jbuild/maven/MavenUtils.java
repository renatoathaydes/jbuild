package jbuild.maven;

import jbuild.artifact.Artifact;
import jbuild.util.NonEmptyCollection;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

public final class MavenUtils {

    public static final String MAVEN_CENTRAL_URL = "https://repo1.maven.org/maven2/";

    private enum XmlSingletons {
        INSTANCE;

        private final DocumentBuilderFactory factory;

        XmlSingletons() {
            factory = DocumentBuilderFactory.newInstance();
            try {
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            } catch (ParserConfigurationException e) {
                throw new RuntimeException("Cannot parse XML without feature: " +
                        XMLConstants.FEATURE_SECURE_PROCESSING);
            }
        }

    }

    public static String standardArtifactPath(Artifact artifact, boolean usePlatformSeparator) {
        var fileName = artifact.toFileName();
        var sep = usePlatformSeparator ? File.separatorChar : '/';

        return artifact.groupId.replace('.', sep) + sep +
                artifact.artifactId + sep +
                artifact.version + sep + fileName;
    }

    private static boolean matches(Dependency dependency, Set<ArtifactKey> exclusions) {
        if (exclusions.contains(ArtifactKey.of(dependency))) return true;
        for (var exclusion : exclusions) {
            if ("*".equals(exclusion.groupId) && exclusion.artifactId.equals(dependency.artifact.artifactId)) {
                return true;
            }
            if ("*".equals(exclusion.artifactId) && exclusion.groupId.equals(dependency.artifact.groupId)) {
                return true;
            }
        }
        return false;
    }

    // dependencyManagement currently does not support exclusions.
    // See https://github.com/apache/maven/pull/295
    public static Set<Dependency> applyExclusions(Set<Dependency> dependencies,
                                                  Set<ArtifactKey> exclusions) {
        if (exclusions.isEmpty()) return dependencies;
        if (exclusions.contains(ArtifactKey.of("*", "*"))) return Set.of();

        // try to avoid allocating a new Set as usually it won't be necessary
        var requiresChanges = false;
        for (var dependency : dependencies) {
            if (matches(dependency, exclusions)) {
                requiresChanges = true;
                break;
            }
        }

        if (!requiresChanges) return dependencies;

        return dependencies.stream()
                .filter(dep -> !matches(dep, exclusions))
                .collect(toSet());
    }

    public static MavenPom parsePom(InputStream stream)
            throws ParserConfigurationException, IOException, SAXException {
        var db = XmlSingletons.INSTANCE.factory.newDocumentBuilder();
        try (stream) {
            return new MavenPom(db.parse(stream));
        }
    }

    public static MavenMetadata parseMavenMetadata(InputStream stream)
            throws ParserConfigurationException, IOException, SAXException {
        var db = XmlSingletons.INSTANCE.factory.newDocumentBuilder();
        try (stream) {
            return new MavenMetadata(db.parse(stream));
        }
    }

    public static Instant parseMavenTimestamp(String timestamp) {
        return LocalDateTime.parse(timestamp,
                        DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT))
                .toInstant(ZoneOffset.UTC);
    }

    public static String resolveProperty(String value, Map<String, String> properties) {
        String result = value;
        while (result.startsWith("${") && result.endsWith("}")) {
            var key = result.substring(2, result.length() - 1);
            if (properties.containsKey(key)) {
                result = properties.get(key);
            } else {
                return result;
            }
        }
        return result;
    }

    public static Set<Artifact> importsOf(MavenPom pom) {
        return pom.getDependencyManagement().values().stream()
                .flatMap(NonEmptyCollection::stream)
                .filter(it -> it.scope == Scope.IMPORT)
                .map(it -> it.artifact)
                .collect(toSet());
    }

    public static String extensionOfPackaging(String packaging) {
        switch (packaging) {
            case "jar":
            case "bundle":
            case "maven-plugin":
                return "jar";
            default:
                return packaging;
        }
    }
}
