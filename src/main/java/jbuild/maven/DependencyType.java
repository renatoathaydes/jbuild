package jbuild.maven;

public enum DependencyType {
    POM, JAR, TEST_JAR, MAVEN_PLUGIN, EJB, EJB_CLIENT, WAR, EAR, RAR, JAVA_SOURCE, JAVADOC, CUSTOM;

    public String getClassifier() {
        switch (this) {
            case TEST_JAR:
                return "tests";
            case EJB_CLIENT:
                return "client";
            case JAVA_SOURCE:
                return "sources";
            case JAVADOC:
                return "javadoc";
            default:
                return "";
        }
    }

    public String getExtension() {
        switch (this) {
            case POM:
                return "pom";
            case WAR:
                return "war";
            case EAR:
                return "ear";
            case RAR:
                return "rar";
            default:
                return "jar";
        }
    }

    public String string() {
        switch (this) {
            case POM:
                return "pom";
            case JAR:
                return "jar";
            case TEST_JAR:
                return "test-jar";
            case MAVEN_PLUGIN:
                return "maven-plugin";
            case EJB:
                return "ejb";
            case EJB_CLIENT:
                return "ejb-client";
            case WAR:
                return "war";
            case EAR:
                return "ear";
            case RAR:
                return "rar";
            case JAVA_SOURCE:
                return "java-source";
            case JAVADOC:
                return "javadoc";
            default:
                return "";
        }
    }

    public static DependencyType fromClassifier(String text) {
        switch (text) {
            case "tests":
                return TEST_JAR;
            case "client":
                return EJB_CLIENT;
            case "sources":
                return JAVA_SOURCE;
            case "javadoc":
                return JAVADOC;
            default:
                return JAR;
        }
    }

    public static DependencyType fromString(String text) {
        switch (text) {
            case "pom":
                return POM;
            case "jar":
                return JAR;
            case "test-jar":
                return TEST_JAR;
            case "maven-plugin":
                return MAVEN_PLUGIN;
            case "ejb":
                return EJB;
            case "ejb-client":
                return EJB_CLIENT;
            case "war":
                return WAR;
            case "ear":
                return EAR;
            case "rar":
                return RAR;
            case "java-source":
                return JAVA_SOURCE;
            case "javadoc":
                return JAVADOC;
            default:
                return CUSTOM;
        }
    }
}
