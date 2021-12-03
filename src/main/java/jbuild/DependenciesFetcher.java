package jbuild;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactResolution;
import jbuild.artifact.ArtifactRetriever;
import jbuild.artifact.file.FileArtifactRetriever;
import jbuild.artifact.http.HttpArtifactRetriever;
import jbuild.errors.ArtifactRetrievalError;
import jbuild.errors.FileRetrievalError;
import jbuild.errors.HttpError;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A dependency fetcher that can use one or more {@link ArtifactRetriever} instances to retrieve a list of artifacts,
 * then optionally, their dependencies as well.
 */
public class DependenciesFetcher {

    public List<CompletableFuture<ArtifactResolution<HttpError<byte[]>>>> fetchAllByHttp(List<Artifact> artifacts) {
        var httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        return fetchAll(artifacts, new HttpArtifactRetriever(httpClient));
    }

    public List<CompletableFuture<ArtifactResolution<FileRetrievalError>>> fetchAllFromFileSystem(
            List<Artifact> artifacts) {
        return fetchAll(artifacts, new FileArtifactRetriever());
    }

    public List<CompletableFuture<ArtifactResolution<FileRetrievalError>>> fetchAllFromFileSystem(
            List<Artifact> artifacts,
            Path repositoryDir) {
        return fetchAll(artifacts, new FileArtifactRetriever(repositoryDir));
    }

    public <Err extends ArtifactRetrievalError> List<CompletableFuture<ArtifactResolution<Err>>> fetchAll(
            List<Artifact> artifacts,
            ArtifactRetriever<Err> retriever) {
        var resolutions = new ArrayList<CompletableFuture<ArtifactResolution<Err>>>(artifacts.size());

        for (var value : artifacts) {
            resolutions.add(retriever.retrieve(value));
        }

        return resolutions;
    }
}
