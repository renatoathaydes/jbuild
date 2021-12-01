package jbuild;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactResolution;
import jbuild.artifact.ArtifactRetriever;
import jbuild.artifact.http.HttpArtifactRetriever;
import jbuild.errors.HttpError;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DependenciesManager {

    public List<CompletableFuture<ArtifactResolution<HttpError<byte[]>>>> downloadAllByHttp(List<Artifact> artifacts) {
        var httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        return downloadAll(artifacts, new HttpArtifactRetriever(httpClient));
    }

    public <Err> List<CompletableFuture<ArtifactResolution<Err>>> downloadAll(
            List<Artifact> artifacts,
            ArtifactRetriever<Err> retriever) {
        var resolutions = new ArrayList<CompletableFuture<ArtifactResolution<Err>>>(artifacts.size());

        for (var value : artifacts) {
            resolutions.add(retriever.retrieve(value));
        }

        return resolutions;
    }
}
