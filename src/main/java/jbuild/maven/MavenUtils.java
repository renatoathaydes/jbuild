package jbuild.maven;

import jbuild.artifact.Artifact;
import jbuild.util.NonEmptyCollection;
import jbuild.util.XmlUtils;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.toSet;
import static jbuild.util.TextUtils.firstNonBlank;

public final class MavenUtils {

    public static final String MAVEN_CENTRAL_URL = "https://repo1.maven.org/maven2/";

    public static Path mavenHome() {
        var mavenHome = System.getenv("MAVEN_HOME");
        if (mavenHome != null && !mavenHome.isBlank()) {
            return Paths.get(mavenHome);
        }
        var userHome = firstNonBlank(System.getProperty("user.home"), File.separator);
        return Paths.get(userHome, ".m2", "repository");
    }

    public static String standardArtifactPath(Artifact artifact, boolean usePlatformSeparator) {
        var fileName = artifact.toFileName();
        var sep = usePlatformSeparator ? File.separatorChar : '/';
        return standardBasePath(artifact, usePlatformSeparator) + artifact.version + sep + fileName;
    }

    public static CharSequence standardBasePath(Artifact artifact, boolean usePlatformSeparator) {
        var sep = usePlatformSeparator ? File.separatorChar : '/';
        return artifact.groupId.replace('.', sep) + sep + artifact.artifactId + sep;
    }

    private static boolean matches(Dependency dependency, Set<Pattern> exclusions) {
        var coordinates = dependency.artifact.getCoordinates();
        for (var pattern : exclusions) {
            if (pattern.matcher(coordinates).matches()) {
                return true;
            }
        }
        return false;
    }

    public static Set<Dependency> applyExclusions(Set<Dependency> dependencies,
                                                  Set<ArtifactKey> exclusions) {
        return applyExclusionPatterns(dependencies, asPatterns(exclusions));
    }

    // dependencyManagement currently does not support exclusions.
    // See https://github.com/apache/maven/pull/295
    public static Set<Dependency> applyExclusionPatterns(Set<Dependency> dependencies,
                                                         Set<Pattern> exclusions) {
        if (exclusions.isEmpty()) return dependencies;

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
        var db = XmlUtils.XmlSingletons.INSTANCE.factory.newDocumentBuilder();
        try (stream) {
            return new MavenPom(db.parse(stream));
        }
    }

    public static MavenArtifactMetadata parseMavenMetadata(InputStream stream)
            throws ParserConfigurationException, IOException, SAXException {
        var db = XmlUtils.XmlSingletons.INSTANCE.factory.newDocumentBuilder();
        try (stream) {
            return new MavenArtifactMetadata(db.parse(stream));
        }
    }

    public static Instant parseMavenTimestamp(String timestamp) {
        return LocalDateTime.parse(timestamp,
                        DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT))
                .toInstant(ZoneOffset.UTC);
    }

    public static String resolveProperty(String value, Map<String, String> properties) {
        String result = value;
        Set<String> visitedProperties = null;
        while (isUnresolvedProperty(result)) {
            var key = result.substring(2, result.length() - 1);
            if (properties.containsKey(key)) {
                if (visitedProperties == null) {
                    visitedProperties = new LinkedHashSet<>(4);
                }
                var isNew = visitedProperties.add(key);
                if (!isNew) {
                    throw new IllegalStateException("infinite loop detected resolving property: " +
                            String.join(" -> ", visitedProperties) + " -> " + key);
                }
                result = properties.get(key);
            } else {
                return result;
            }
        }
        return result;
    }

    public static boolean isUnresolvedProperty(String value) {
        return value.startsWith("${") && value.endsWith("}");
    }

    private static boolean isUnresolvedPropertyOrEmpty(String value) {
        return isUnresolvedProperty(value) || value.isBlank();
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
            case "eclipse-plugin":
                return "jar";
            default:
                return packaging;
        }
    }

    public static boolean isFullyResolved(License license) {
        return !isUnresolvedPropertyOrEmpty(license.name) &&
                !isUnresolvedPropertyOrEmpty(license.url);
    }

    public static Set<Pattern> asPatterns(Set<ArtifactKey> exclusions) {
        return exclusions.stream()
                .map(MavenUtils::patternOf)
                .collect(toSet());
    }

    private static Pattern patternOf(ArtifactKey key) {
        var group = key.groupId;
        var id = key.artifactId;

        if ("*".equals(group)) {
            group = ".*";
        } else {
            group = Pattern.quote(group);
        }
        if ("*".equals(id)) {
            id = ".*";
        } else {
            id = Pattern.quote(id);
        }
        return Pattern.compile(group + ':' + id + ':' + ".*");
    }

}
