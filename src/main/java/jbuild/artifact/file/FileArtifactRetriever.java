package jbuild.artifact.file;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactResolution;
import jbuild.artifact.ArtifactRetriever;
import jbuild.artifact.ResolvedArtifact;
import jbuild.errors.FileRetrievalError;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static jbuild.maven.MavenUtils.standardArtifactPath;
import static jbuild.util.FileUtils.readAllBytes;
import static jbuild.util.TextUtils.firstNonBlank;

public class FileArtifactRetriever implements ArtifactRetriever<FileRetrievalError> {

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
        var userHome = firstNonBlank(System.getProperty("user.home"), File.separator);
        return Paths.get(userHome, ".m2", "repository");
    }

    @Override
    public String getDescription() {
        return "file-repository[" + rootDir + "]";
    }

    @Override
    public CompletionStage<ArtifactResolution<FileRetrievalError>> retrieve(Artifact artifact) {
        var path = standardArtifactPath(artifact, true);
        var file = rootDir.resolve(Paths.get(path));
        var requestTime = System.currentTimeMillis();

        if (file.toFile().isFile()) {
            // we do not handle files so long that their length won't fit into an int
            // because we wouldn't even be able to return an array if we did that.
            return readAllBytes(file).handle((bytes, err) ->
                    err != null
                            ? completeWith(artifact, err)
                            : completeWith(artifact, bytes, requestTime));
        } else {
            return completedFuture(completeWith(artifact, new FileNotFoundException(file.toString())));
        }
    }

    private ArtifactResolution<FileRetrievalError> completeWith(Artifact artifact, byte[] bytes, long requestTime) {
        return ArtifactResolution.success(new ResolvedArtifact(bytes, artifact, this, requestTime));
    }

    private ArtifactResolution<FileRetrievalError> completeWith(Artifact artifact, Throwable error) {
        return ArtifactResolution.failure(new FileRetrievalError(this, artifact, error));
    }

}
