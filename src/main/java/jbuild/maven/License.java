package jbuild.maven;

/**
 * The license that applies to a project.
 * <p>
 * See <a href="https://spdx.org/licenses/">Spdx Licenses</a>.
 */
public final class License {

    public final String name;
    public final String url;

    public License(String name, String url) {
        this.name = name;
        this.url = url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        License license = (License) o;

        if (!name.equals(license.name)) return false;
        return url.equals(license.url);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + url.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "License{" +
                "name='" + name + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
