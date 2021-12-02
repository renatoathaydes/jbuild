package jbuild.util;

import jbuild.artifact.Artifact;

import java.io.File;

public final class MavenUtils {

    public static String standardArtifactPath(Artifact artifact, boolean usePlatformSeparator) {
        var fileName = artifact.toFileName();
        var sep = usePlatformSeparator ? File.separatorChar : '/';

        return artifact.groupId.replace('.', sep) + sep +
                artifact.artifactId + sep +
                artifact.version + sep + fileName;
    }
}
