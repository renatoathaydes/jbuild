package jbuild.artifact.file;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactResolution;
import jbuild.artifact.ArtifactRetriever;
import jbuild.artifact.ResolvedArtifact;
import jbuild.util.MavenUtils;

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

import static jbuild.util.FileUtils.readAllBytes;

public class FileArtifactRetriever implements ArtifactRetriever<Throwable> {

    private final Path rootDir;

    public FileArtifactRetriever(Path rootDir) {
        this.rootDir = rootDir;
    }

    public FileArtifactRetriever() {
        this(mavenHome());
    }

    private static Path mavenHome() {
        var mavenHome = System.getenv("MAVEN_HOME");
        if (mavenHome != null && !mavenHome.isBlank()) {
            return Paths.get(mavenHome);
        }
        var userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".m2", "repository");
    }

    @Override
    public CompletableFuture<ArtifactResolution<Throwable>> retrieve(Artifact artifact) {
        var path = MavenUtils.standardArtifactPath(artifact, true);
        var file = rootDir.resolve(Paths.get(path));
        if (file.toFile().isFile()) {
            // we do not handle files so long that their length won't fit into an int
            // because we wouldn't even be able to return an array if we did that.
            return readAllBytes(file).thenApply(bytes -> completeWith(artifact, bytes));
        } else {
            return CompletableFuture.completedFuture(
                    ArtifactResolution.failure(new FileNotFoundException(file.toString()), artifact));
        }
    }

    private ArtifactResolution<Throwable> completeWith(Artifact artifact, byte[] bytes) {
        return ArtifactResolution.success(new ResolvedArtifact(bytes, artifact));
    }
}
