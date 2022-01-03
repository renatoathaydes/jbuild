package jbuild;

public final class TestSystemProperties {

    public static final String otherClassesJar = System.getProperty("tests.other-test-classes.jar");
    public static final String myClassesJar = System.getProperty("tests.my-test-classes.jar");
    public static final String testJarsDir = System.getProperty("tests.test-classes.dir");
    public static final String osgiaasCliApiJar = System.getProperty("tests.real-jars.osgiaas-cli-api.jar");

}
