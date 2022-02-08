package jbuild.artifact;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionRangeTest {

    static Object[][] singleVersionRanges() {
        return new Object[][]{
                new Object[]{"", new Version(0, 0, 0, ""), new Version(0, 0, 0, ""), true, true},
                new Object[]{"1", new Version(1, 0, 0, ""), new Version(1, 0, 0, ""), true, true},
                new Object[]{"1.2.3", new Version(1, 2, 3, ""), new Version(1, 2, 3, ""), true, true},
                new Object[]{"[,1]", new Version(0, 0, 0, ""), new Version(1, 0, 0, ""), true, true},
                new Object[]{"[,2.3.4)", new Version(0, 0, 0, ""), new Version(2, 3, 4, ""), true, false},
                new Object[]{"(,)", new Version(0, 0, 0, ""),
                        new Version(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, ""), false, false},
                new Object[]{"(1.0,)", new Version(1, 0, 0, ""),
                        new Version(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, ""), false, false},
                new Object[]{"(1.0 , 2.0)", new Version(1, 0, 0, ""), new Version(2, 0, 0, ""), false, false},
                new Object[]{" [10, 200 ]", new Version(10, 0, 0, ""), new Version(200, 0, 0, ""), true, true},
        };
    }

    @MethodSource("singleVersionRanges")
    @ParameterizedTest
    void canParseSingleVersionRange(String range, Version min, Version max, boolean minIncl, boolean maxIncl) {
        var versionRange = VersionRange.parse(range);
        var interval = versionRange.intervals.first;
        assertThat(interval.min).isEqualTo(min);
        assertThat(interval.max).isEqualTo(max);
        assertThat(interval.minInclusive).isEqualTo(minIncl);
        assertThat(interval.maxInclusive).isEqualTo(maxIncl);
    }

    static Object[][] versionInclusionExamples() {
        return new Object[][]{
                {"(,2.0)", new Version(0, 0, 0), new Version(1, 0, 0), new Version(2, 0, 0)},
                {"[0.1,0.2.0]", new Version(0, 0, 0), new Version(0, 2, 0), new Version(0, 3, 0)},
                {"(0.0.19,0.0.21)", new Version(0, 0, 10), new Version(0, 0, 20), new Version(0, 0, 30)},
                {"(1,2), (4,6) , (10,)",
                        new Version(0, 0, 0), new Version(1, 4, 0), new Version(8, 0, 0)},
                {"(1,2), (4,6) , (10,)",
                        new Version(3, 0, 0), new Version(5, 0, 0), new Version(8, 0, 0)},
                {"(1,2), (4,6) , (10,)",
                        new Version(6, 0, 0), new Version(10, 0, 1), new Version(8, 0, 0)},
                {"(1,2), (4,6) , [10,)",
                        new Version(6, 0, 0), new Version(10, 0, 0), new Version(6, 0, 0)},
                {"[1,3),[4,5]",
                        new Version(0, 0, 0), new Version(1, 0, 0), new Version(3, 0, 0)},
                {"[1,3),[4,5]",
                        new Version(0, 0, 0), new Version(4, 0, 0), new Version(8, 0, 0)},
                {"[1,3),[4,5]",
                        new Version(0, 0, 0), new Version(5, 0, 0), new Version(100, 0, 0)},
                {"[1,3),[4,5],[10,]",
                        new Version(3, 0, 0), new Version(5, 0, 0), new Version(8, 0, 0)},
                {"[1,3),[4,5],[10,]",
                        new Version(3, 0, 0), new Version(10, 0, 0), new Version(8, 0, 0)},
                {"[1,3),[4,5],[10,]",
                        new Version(3, 0, 0), new Version(999, 999, 999), new Version(8, 0, 0)},
        };
    }

    @MethodSource("versionInclusionExamples")
    @ParameterizedTest
    void canCheckVersionInclusion(String range, Version lower, Version inside, Version higher) {
        var versionRange = VersionRange.parse(range);
        assertThat(versionRange.contains(lower))
                .withFailMessage(lower + " should be outside " + range)
                .isFalse();
        assertThat(versionRange.contains(inside))
                .withFailMessage(inside + " should be inside " + range)
                .isTrue();
        assertThat(versionRange.contains(higher))
                .withFailMessage(higher + " should be outside " + range)
                .isFalse();
    }

}
