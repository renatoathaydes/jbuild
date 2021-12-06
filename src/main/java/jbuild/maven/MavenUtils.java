package jbuild.maven;

import jbuild.artifact.Artifact;
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

public final class MavenUtils {

    public static final String MAVEN_CENTRAL_URL = "https://repo1.maven.org/maven2";

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

    public static MavenPom parsePom(InputStream stream) throws ParserConfigurationException, IOException, SAXException {
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
        if (value.startsWith("${") && value.endsWith("}")) {
            var key = value.substring(2, value.length() - 1);
            return properties.getOrDefault(key, value);
        }
        return value;
    }
}
