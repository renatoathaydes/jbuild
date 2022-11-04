package jbuild.maven;

import jbuild.artifact.Artifact;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.List;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class PublishableMavenPomTest {

    @Test
    void canWriteSimplePom() {
        var pom = new PublishableMavenPom(
                new Artifact("com.example", "my-artifact", "1.2.3"),
                "my artifact",
                "My artifact",
                URI.create("http://my-project.com/artifact"),
                List.of(new License("Apache", "http://apache.org/license")),
                List.of(new Developer("Renato", "renato@example.com",
                        new Organization("Example", URI.create("https://example.com")))),
                new Scm(URI.create("scm:git:git://github.com/bowbahdoe/java-datastructures.git"),
                        URI.create("scm:git:ssh://github.com:bowbahdoe/java-datastructures.git"),
                        URI.create("https://github.com/bowbahdoe/java-datastructures/tree/main")),
                List.of(new Dependency(new Artifact("com.acme", "dep", "0.1"))));

        var out = new ByteArrayOutputStream(512);

        pom.writeTo(out);

        assertThat(out.toString(UTF_8)).isEqualTo(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><project>\n" +
                        "  <modelVersion>4.0.0</modelVersion>\n" +
                        "  <groupId>com.example</groupId>\n" +
                        "  <artifactId>my-artifact</artifactId>\n" +
                        "  <version>1.2.3</version>\n" +
                        "  <packaging>jar</packaging>\n" +
                        "  <name>my artifact</name>\n" +
                        "  <description>My artifact</description>\n" +
                        "  <url>http://my-project.com/artifact</url>\n" +
                        "  <licenses>\n" +
                        "    <license>\n" +
                        "      <name>Apache</name>\n" +
                        "      <url>http://apache.org/license</url>\n" +
                        "    </license>\n" +
                        "  </licenses>\n" +
                        "  <developers>\n" +
                        "    <developer>\n" +
                        "      <name>Renato</name>\n" +
                        "      <email>renato@example.com</email>\n" +
                        "      <organization>Example</organization>\n" +
                        "      <organizationUrl>https://example.com</organizationUrl>\n" +
                        "    </developer>\n" +
                        "  </developers>\n" +
                        "  <scm>\n" +
                        "    <connection>scm:git:git://github.com/bowbahdoe/java-datastructures.git</connection>\n" +
                        "    <developerConnection>scm:git:ssh://github.com:bowbahdoe/java-datastructures.git</developerConnection>\n" +
                        "    <url>https://github.com/bowbahdoe/java-datastructures/tree/main</url>\n" +
                        "  </scm>\n" +
                        "  <dependencies>\n" +
                        "    <dependency>\n" +
                        "      <groupId>com.acme</groupId>\n" +
                        "      <artifactId>dep</artifactId>\n" +
                        "      <version>0.1</version>\n" +
                        "    </dependency>\n" +
                        "  </dependencies>\n" +
                        "</project>\n");

    }

    @Test
    void canWriteLargerPom() {
        var pom = new PublishableMavenPom(
                new Artifact("com.example", "my-artifact", "1.2.3"),
                "my artifact",
                "My artifact",
                URI.create("http://my-project.com/artifact"),
                List.of(new License("Apache", "http://apache.org/license"),
                        new License("MIT", "http://mit.edu/license")),
                List.of(new Developer("Renato", "renato@example.com",
                                new Organization("Example", URI.create("https://example.com"))),
                        new Developer("John", "john@example.com",
                                new Organization("Example", URI.create("https://example.com")))),
                new Scm(URI.create("scm:git:git://foo.com/foo.git"),
                        URI.create("scm:git:ssh://bar.com:bar/bar.git"),
                        URI.create("https://zort.com/zort")),
                List.of(new Dependency(new Artifact("com.acme", "dep", "0.1"), Scope.TEST, "true", Set.of(), "", false),
                        new Dependency(new Artifact("com.example", "foo-bar", "1.0-dev"),
                                Scope.PROVIDED, "", Set.of(
                                ArtifactKey.of("gx", ""),
                                ArtifactKey.of("gy", "ab")),
                                "", true)));

        var out = new ByteArrayOutputStream(512);

        pom.writeTo(out);

        assertThat(out.toString(UTF_8)).isEqualTo(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><project>\n" +
                        "  <modelVersion>4.0.0</modelVersion>\n" +
                        "  <groupId>com.example</groupId>\n" +
                        "  <artifactId>my-artifact</artifactId>\n" +
                        "  <version>1.2.3</version>\n" +
                        "  <packaging>jar</packaging>\n" +
                        "  <name>my artifact</name>\n" +
                        "  <description>My artifact</description>\n" +
                        "  <url>http://my-project.com/artifact</url>\n" +
                        "  <licenses>\n" +
                        "    <license>\n" +
                        "      <name>Apache</name>\n" +
                        "      <url>http://apache.org/license</url>\n" +
                        "    </license>\n" +
                        "    <license>\n" +
                        "      <name>MIT</name>\n" +
                        "      <url>http://mit.edu/license</url>\n" +
                        "    </license>\n" +
                        "  </licenses>\n" +
                        "  <developers>\n" +
                        "    <developer>\n" +
                        "      <name>Renato</name>\n" +
                        "      <email>renato@example.com</email>\n" +
                        "      <organization>Example</organization>\n" +
                        "      <organizationUrl>https://example.com</organizationUrl>\n" +
                        "    </developer>\n" +
                        "    <developer>\n" +
                        "      <name>John</name>\n" +
                        "      <email>john@example.com</email>\n" +
                        "      <organization>Example</organization>\n" +
                        "      <organizationUrl>https://example.com</organizationUrl>\n" +
                        "    </developer>\n" +
                        "  </developers>\n" +
                        "  <scm>\n" +
                        "    <connection>scm:git:git://foo.com/foo.git</connection>\n" +
                        "    <developerConnection>scm:git:ssh://bar.com:bar/bar.git</developerConnection>\n" +
                        "    <url>https://zort.com/zort</url>\n" +
                        "  </scm>\n" +
                        "  <dependencies>\n" +
                        "    <dependency>\n" +
                        "      <groupId>com.acme</groupId>\n" +
                        "      <artifactId>dep</artifactId>\n" +
                        "      <version>0.1</version>\n" +
                        "      <optional>true</optional>\n" +
                        "      <scope>test</scope>\n" +
                        "    </dependency>\n" +
                        "    <dependency>\n" +
                        "      <groupId>com.example</groupId>\n" +
                        "      <artifactId>foo-bar</artifactId>\n" +
                        "      <version>1.0-dev</version>\n" +
                        "      <scope>provided</scope>\n" +
                        "      <exclusions>\n" +
                        "        <exclusion>\n" +
                        "          <groupId>gx</groupId>\n" +
                        "        </exclusion>\n" +
                        "        <exclusion>\n" +
                        "          <groupId>gy</groupId>\n" +
                        "          <artifactId>ab</artifactId>\n" +
                        "        </exclusion>\n" +
                        "      </exclusions>\n" +
                        "    </dependency>\n" +
                        "  </dependencies>\n" +
                        "</project>\n" +
                        "");

    }
}
