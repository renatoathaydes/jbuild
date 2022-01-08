package jbuild;

import java.io.File;

public final class TestSystemProperties {

    public static final File otherClassesJar = new File(System.getProperty("tests.other-test-classes.jar", "unset"));
    public static final File myClassesJar = new File(System.getProperty("tests.my-test-classes.jar", "unset"));
    public static final File testJarsDir = new File(System.getProperty("tests.test-classes.dir", "unset"));
    public static final File osgiaasCliApiJar = new File(System.getProperty("tests.real-jars.osgiaas-cli-api.jar", "unset"));

    public static final int javaVersion;

    static {
        var javaVer = System.getProperty("java.version", "");
        if (javaVer.matches("\\d+\\..+")) {
            javaVersion = Integer.parseInt(javaVer.substring(0, javaVer.indexOf('.')));
        } else {
            javaVersion = -1;
        }
    }
}
