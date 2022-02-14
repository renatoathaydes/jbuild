package jbuild.artifact;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static jbuild.artifact.Version.MIN_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

public class VersionTest {

    static Stream<Object> versionExamples() {
        return Stream.of(
                new Object[]{"", MIN_VERSION},
                new Object[]{"1", new Version(1, 0, 0, "")},
                new Object[]{"1.1", new Version(1, 1, 0, "")},
                new Object[]{"1.1.1", new Version(1, 1, 1, "")},
                new Object[]{"1.2.3.4", new Version(1, 2, 3, "4")},
                new Object[]{"foo", new Version(0, 0, 0, "foo")},
                new Object[]{"10.foo.3", new Version(10, 0, 0, "foo.3")},
                new Object[]{"1-2-3.alpha", new Version(1, 2, 3, "alpha")},
                new Object[]{"4.5.6-beta-1", new Version(4, 5, 6, "beta-1")},
                new Object[]{"4a.5.6-beta-1", new Version(0, 0, 0, "4a.5.6-beta-1")},
                new Object[]{"1.0.0-alpha+001", new Version(1, 0, 0, "alpha+001")},
                new Object[]{"1.0.0-0.3.7", new Version(1, 0, 0, "0.3.7")}
        );
    }

    static Version[][] sortedVersionExamples() {
        return new Version[][]{
                {MIN_VERSION, new Version(1, 0, 0, ""), new Version(2, 0, 0, "")},
                {new Version(0, 1, 0, ""), new Version(0, 2, 0, ""), new Version(0, 3, 0, "")},
                {new Version(0, 0, 10, ""), new Version(0, 0, 20, ""), new Version(0, 0, 30, "")},
                {new Version(1, 0, 0, "alpha"), new Version(1, 0, 0, "zeta"), new Version(1, 0, 0, "")},
                {new Version(0, 0, 0, "a"), new Version(0, 0, 0, "b"), MIN_VERSION},
                {new Version(0, 0, 0, "a"), new Version(0, 0, 0, "b"), new Version(0, 0, 0, "c")},
                {new Version(10, 3, 0, ""), new Version(20, 2, 0, ""), new Version(30, 1, 0, "")},
                {new Version(10, 0, 3, ""), new Version(20, 0, 2, ""), new Version(30, 0, 1, "")},
                {new Version(10, 0, 0, "c"), new Version(20, 0, 0, "b"), new Version(30, 0, 0, "a")},
                {new Version(10, 0, 0, "c"), new Version(20, 0, 0, "b"), new Version(30, 0, 0, "a")},
        };
    }

    @MethodSource("versionExamples")
    @ParameterizedTest
    void canParseVersion(String version, Version expectedVersion) {
        assertThat(Version.parse(version)).isEqualTo(expectedVersion);
    }

    @MethodSource("sortedVersionExamples")
    @ParameterizedTest
    void canSortVersions(Version v1, Version v2, Version v3) {
        assertThat(v1).isLessThan(v2);
        assertThat(v2).isLessThan(v3);
        assertThat(v1).isLessThan(v3);
    }

}
