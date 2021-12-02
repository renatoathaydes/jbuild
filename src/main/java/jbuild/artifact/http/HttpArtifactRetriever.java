package jbuild.artifact.http;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactResolution;
import jbuild.artifact.ArtifactRetriever;
import jbuild.artifact.ResolvedArtifact;
import jbuild.errors.HttpError;
import jbuild.util.MavenUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class HttpArtifactRetriever implements ArtifactRetriever<HttpError<byte[]>> {

    public static final String MAVEN_CENTRAL_URL = "https://repo1.maven.org/maven2";

    private final HttpClient httpClient;
    private final String baseUrl;

    public HttpArtifactRetriever(HttpClient httpClient,
                                 String baseUrl) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
    }

    public HttpArtifactRetriever(HttpClient httpClient) {
        this(httpClient, MAVEN_CENTRAL_URL);
    }

    @Override
    public CompletableFuture<ArtifactResolution<HttpError<byte[]>>> retrieve(Artifact artifact) {
        var requestPath = MavenUtils.standardArtifactPath(artifact, false);

        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/" + requestPath)).build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
            if (response.statusCode() == 200) {
                return ArtifactResolution.success(new ResolvedArtifact(response.body(), artifact));
            }
            return ArtifactResolution.failure(new HttpError<>(response), artifact);
        });
    }
}
