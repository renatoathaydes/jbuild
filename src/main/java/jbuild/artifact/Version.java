package jbuild.artifact;

/**
 * Semantic version.
 */
public final class Version implements Comparable<Version> {

    public final int major;
    public final int minor;
    public final int patch;
    public final String qualifier;

    public Version(int major, int minor, int patch, String qualifier) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.qualifier = qualifier;
    }

    public Version(int major, int minor, int patch) {
        this(major, minor, patch, "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Version version = (Version) o;

        if (major != version.major) return false;
        if (minor != version.minor) return false;
        if (patch != version.patch) return false;
        return qualifier.equals(version.qualifier);
    }

    @Override
    public int hashCode() {
        int result = major;
        result = 31 * result + minor;
        result = 31 * result + patch;
        result = 31 * result + qualifier.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch + (qualifier.isBlank() ? "" : "-" + qualifier);
    }

    @Override
    public int compareTo(Version other) {
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }
        if (minor != other.minor) {
            return Integer.compare(minor, other.minor);
        }
        if (patch != other.patch) {
            return Integer.compare(patch, other.patch);
        }
        if (qualifier.isBlank()) {
            return other.qualifier.isBlank() ? 0 : 1;
        }
        if (other.qualifier.isBlank()) {
            return -1;
        }
        return qualifier.compareTo(other.qualifier);
    }

    public boolean isAfter(Version version) {
        return compareTo(version) > 0;
    }

    public boolean isBefore(Version version) {
        return compareTo(version) < 0;
    }

    public static Version parse(String version) {
        var parts = version.split("[+.\\-]", 4);
        int major = 0, minor = 0, patch = 0;
        if (parts.length == 0) {
            return new Version(major, minor, patch, "");
        }
        try {
            major = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return new Version(0, 0, 0, version);
        }
        if (parts.length > 1) try {
            minor = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            var qualifier = version.substring(parts[0].length() + 1);
            return new Version(major, 0, 0, qualifier);
        }
        else {
            return new Version(major, minor, patch, "");
        }
        if (parts.length > 2) try {
            patch = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            var qualifier = version.substring(parts[0].length() + parts[1].length() + 2);
            return new Version(major, minor, 0, qualifier);
        }
        else {
            return new Version(major, minor, patch, "");
        }
        var qualifier = parts.length > 3 ? parts[3] : "";
        return new Version(major, minor, patch, qualifier);
    }
}
