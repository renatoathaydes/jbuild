package jbuild;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactResolution;
import jbuild.artifact.ArtifactRetriever;
import jbuild.artifact.http.HttpArtifactRetriever;
import jbuild.artifact.util.AsyncUtils;
import jbuild.errors.HttpError;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

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

    public static void main(String[] args) {
        var artifacts = Stream.of(args)
                .map(Artifact::parseCoordinates)
                .collect(toList());

        var results = AsyncUtils.waitForAll(new DependenciesManager().downloadAllByHttp(artifacts));

        for (var result : results) {
            result.use(
                    resolved -> System.out.println("Resolved " + resolved.artifact + ": " + resolved.contents.length + " bytes"),
                    error -> System.out.println("ERROR: http status = " + error.httpResponse.statusCode() + ", http body = " +
                            new String(error.httpResponse.body(), StandardCharsets.UTF_8)));
        }
    }
}
