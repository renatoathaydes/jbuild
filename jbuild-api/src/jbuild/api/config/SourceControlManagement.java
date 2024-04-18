package jbuild.api.config;

public final class SourceControlManagement {
    public final String connection;
    public final String developerConnection;
    public final String url;

    public SourceControlManagement(String connection, String developerConnection, String url) {
        this.connection = connection;
        this.developerConnection = developerConnection;
        this.url = url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SourceControlManagement that = (SourceControlManagement) o;

        if (!connection.equals(that.connection)) return false;
        if (!developerConnection.equals(that.developerConnection)) return false;
        return url.equals(that.url);
    }

    @Override
    public int hashCode() {
        int result = connection.hashCode();
        result = 31 * result + developerConnection.hashCode();
        result = 31 * result + url.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "SourceControlManagement{" +
                "connection='" + connection + '\'' +
                ", developerConnection='" + developerConnection + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
