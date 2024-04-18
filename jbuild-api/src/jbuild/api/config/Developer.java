package jbuild.api.config;

public final class Developer {
    public final String name;
    public final String email;
    public final String organization;
    public final String organizationUrl;

    public Developer(String name, String email, String organization, String organizationUrl) {
        this.name = name;
        this.email = email;
        this.organization = organization;
        this.organizationUrl = organizationUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Developer developer = (Developer) o;

        if (!name.equals(developer.name)) return false;
        if (!email.equals(developer.email)) return false;
        if (!organization.equals(developer.organization)) return false;
        return organizationUrl.equals(developer.organizationUrl);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + email.hashCode();
        result = 31 * result + organization.hashCode();
        result = 31 * result + organizationUrl.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Developer{" +
                "name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", organization='" + organization + '\'' +
                ", organizationUrl='" + organizationUrl + '\'' +
                '}';
    }
}
