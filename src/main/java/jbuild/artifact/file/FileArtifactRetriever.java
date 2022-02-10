package jbuild.artifact.file;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactMetadata;
import jbuild.artifact.ArtifactResolution;
import jbuild.artifact.ArtifactRetriever;
import jbuild.artifact.ResolvedArtifact;
import jbuild.artifact.Version;
import jbuild.errors.FileRetrievalError;
import jbuild.errors.JBuildException;
import jbuild.util.Either;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;
import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.maven.MavenUtils.standardArtifactPath;
import static jbuild.maven.MavenUtils.standardBasePath;
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

    @Override
    public CompletionStage<Either<? extends ArtifactMetadata, FileRetrievalError>> fetchMetadata(Artifact artifact) {
        var path = standardBasePath(artifact, true).toString();
        var dir = rootDir.resolve(Paths.get(path));
        return CompletableFuture.supplyAsync(() -> {
            var versions = directoriesUnder(dir);
            if (versions.isEmpty()) {
                return Either.right(new FileRetrievalError(this, artifact,
                        new JBuildException("no version of " + artifact.getCoordinates() + " is available", ACTION_ERROR)));
            }
            var allVersions = versions.stream()
                    .map(File::getName)
                    .map(Version::parse)
                    .sorted()
                    .map(Version::toString)
                    .collect(toList());
            var latestVersion = allVersions.get(allVersions.size() - 1);
            return Either.left(ArtifactMetadata.of(artifact, null, latestVersion,
                    new LinkedHashSet<>(allVersions)));
        });
    }

    private static List<File> directoriesUnder(Path dir) {
        var files = dir.toFile().listFiles();
        if (files == null || files.length == 0) return List.of();
        return Arrays.stream(files).filter(File::isDirectory).collect(toList());
    }

    private ArtifactResolution<FileRetrievalError> completeWith(Artifact artifact, byte[] bytes, long requestTime) {
        return ArtifactResolution.success(new ResolvedArtifact(bytes, artifact, this, requestTime));
    }

    private ArtifactResolution<FileRetrievalError> completeWith(Artifact artifact, Throwable error) {
        return ArtifactResolution.failure(new FileRetrievalError(this, artifact, error));
    }

}
