package jbuild.java;

public class JavaVersionHelper {

    public static int currentJavaVersion() {
        var versionString = System.getProperty("java.version");
        return parseJavaVersion(versionString);
    }

    static int parseJavaVersion(String versionString) {
        var dotIndex = versionString.indexOf('.');
        if (dotIndex <= 0) {
            return parseEarlyReleaseVersion(versionString);
        }
        try {
            var firstDigit = Integer.parseInt(versionString.substring(0, dotIndex));
            if (firstDigit == 1) {
                var secondIndex = versionString.indexOf('.', dotIndex + 1);
                if (secondIndex < 0) {
                    secondIndex = versionString.length();
                }
                return Integer.parseInt(versionString.substring(dotIndex + 1, secondIndex));
            }
            return firstDigit;
        } catch (NumberFormatException e) {
            return error(versionString);
        }
    }

    private static int parseEarlyReleaseVersion(String versionString) {
        var dashIndex = versionString.indexOf('-');
        if (dashIndex <= 0) {
            return error(versionString);
        }
        try {
            return Integer.parseInt(versionString.substring(0, dashIndex));
        } catch (NumberFormatException e) {
            return error(versionString);
        }
    }

    private static int error(String versionString) {
        throw new IllegalStateException("java.version system property not a recognizable version: '" + versionString + '\'');
    }

}
