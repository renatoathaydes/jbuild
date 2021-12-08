package jbuild.artifact.http;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactResolution;
import jbuild.artifact.ArtifactRetriever;
import jbuild.artifact.ResolvedArtifact;
import jbuild.errors.HttpError;
import jbuild.maven.MavenUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletionStage;

public class HttpArtifactRetriever implements ArtifactRetriever<HttpError> {

    private final HttpClient httpClient;
    private final String baseUrl;

    public HttpArtifactRetriever(HttpClient httpClient,
                                 String baseUrl) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
    }

    public HttpArtifactRetriever(HttpClient httpClient) {
        this(httpClient, MavenUtils.MAVEN_CENTRAL_URL);
    }

    public HttpArtifactRetriever() {
        this(DefaultHttpClient.get());
    }

    @Override
    public String getDescription() {
        return "http-repository[" + baseUrl + "]";
    }

    @Override
    public CompletionStage<ArtifactResolution<HttpError>> retrieve(Artifact artifact) {
        var requestPath = MavenUtils.standardArtifactPath(artifact, false);

        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/" + requestPath)).build();

        var requestTime = System.currentTimeMillis();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
            if (response.statusCode() == 200) {
                return ArtifactResolution.success(new ResolvedArtifact(response.body(), artifact, this, requestTime));
            }
            return ArtifactResolution.failure(new HttpError(artifact, this, response));
        });
    }
}
