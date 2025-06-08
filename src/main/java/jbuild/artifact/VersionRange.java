package jbuild.artifact;

import jbuild.util.NonEmptyCollection;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

import static jbuild.artifact.Version.MAX_VERSION;
import static jbuild.artifact.Version.MIN_VERSION;

public final class VersionRange {

    public static final VersionRange ANYTHING = new VersionRange(NonEmptyCollection.of(
            new Interval(MIN_VERSION, MAX_VERSION, true, true)));

    public final NonEmptyCollection<Interval> intervals;

    public VersionRange(NonEmptyCollection<Interval> intervals) {
        this.intervals = intervals;
    }

    public boolean contains(Version version) {
        for (var interval : intervals) {
            if (interval.contains(version)) return true;
        }
        return false;
    }

    public Optional<Version> selectLatest(Set<String> versions) {
        return versions.stream().map(Version::parse).sorted()
                .filter(this::contains)
                .max(Version::compareTo);
    }

    @Override
    public String toString() {
        return "VersionRange{" +
                "intervals=" + intervals +
                '}';
    }

    public static boolean isVersionRange(String version) {
        return version.startsWith("[") || version.startsWith("(");
    }

    public static VersionRange parse(String range) {
        if (range.isBlank()) return ANYTHING;
        var intervals = new ArrayList<Interval>(2);
        var index = 0;
        while ((index = nextNonWhitespaceCharIndex(range, index)) < range.length()) {
            var minIncl = range.startsWith("[", index);
            var minExcl = range.startsWith("(", index);
            if (!minIncl && !minExcl) {
                var ver = Version.parse(range.substring(index));
                intervals.add(new Interval(ver, ver, true, true));
                return new VersionRange(NonEmptyCollection.of(intervals));
            }
            var closeInclIndex = range.indexOf(']', index);
            var closeExclIndex = range.indexOf(')', index);
            if (closeInclIndex < 0 && closeExclIndex < 0) {
                throw new IllegalArgumentException("Version range starting at " + index + " not closed: '" + range + "'");
            }
            var endIndex = (closeExclIndex < 0)
                    ? closeInclIndex
                    : (closeInclIndex < 0)
                    ? closeExclIndex
                    : Math.min(closeExclIndex, closeInclIndex);
            intervals.add(createInterval(range, index + 1, endIndex, minIncl, endIndex == closeInclIndex));
            index = endIndex + 1;
            var nextCharIndex = nextNonWhitespaceCharIndex(range, index);
            if (nextCharIndex < range.length()) {
                if (range.charAt(nextCharIndex) != ',') {
                    throw new IllegalArgumentException("Expected ',' at index " + nextCharIndex + ": '" + range + "'");
                }
                index = nextCharIndex + 1;
            }
        }
        if (intervals.isEmpty()) {
            return new VersionRange(NonEmptyCollection.of(createInterval("", 0, 0, true, true)));
        }
        return new VersionRange(NonEmptyCollection.of(intervals));
    }

    private static Interval createInterval(String range,
                                           int startIndex, int endIndex,
                                           boolean minIncl, boolean maxIncl) {
        var parts = range.substring(startIndex, endIndex).split(",", 2);
        if (parts.length == 0) {
            return createInterval(range);
        }
        if (parts.length == 1) {
            return createInterval(parts[0]);
        }
        var min = parts[0].trim();
        var max = parts[1].trim();
        var minVer = min.isEmpty() ? MIN_VERSION : Version.parse(min);
        var maxVer = max.isEmpty() ? MAX_VERSION : Version.parse(max);
        return new Interval(minVer, maxVer, minIncl, maxIncl);
    }

    private static Interval createInterval(String version) {
        var ver = Version.parse(version.trim());
        return new Interval(ver, ver, true, true);
    }

    private static int nextNonWhitespaceCharIndex(String range, int index) {
        while (index < range.length() && range.charAt(index) == ' ') {
            index++;
        }
        return index;
    }

    public static final class Interval {

        public final Version min;
        public final Version max;
        public final boolean minInclusive;
        public final boolean maxInclusive;

        public Interval(Version min, Version max, boolean minInclusive, boolean maxInclusive) {
            this.min = min;
            this.max = max;
            this.minInclusive = minInclusive;
            this.maxInclusive = maxInclusive;
            if (min.compareTo(max) > 0) {
                throw new IllegalArgumentException("min version (" + min + ") > (" + max + ") max version");
            }
        }

        public boolean contains(Version version) {
            var minComp = version.compareTo(min);
            if (minComp < 0) return false;
            if (minComp == 0) return minInclusive;
            var maxComp = version.compareTo(max, false);
            if (maxComp > 0) return false;
            return maxInclusive || maxComp != 0;
        }

        @Override
        public String toString() {
            return "Interval{" +
                    "min=" + min +
                    ", max=" + max +
                    ", minInclusive=" + minInclusive +
                    ", maxInclusive=" + maxInclusive +
                    '}';
        }
    }

}
