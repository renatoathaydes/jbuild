package jbuild.artifact.http;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactResolution;
import jbuild.artifact.ArtifactRetriever;
import jbuild.artifact.ResolvedArtifact;
import jbuild.errors.HttpError;
import jbuild.maven.MavenUtils;
import jbuild.util.Either;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedStage;

public class HttpArtifactRetriever implements ArtifactRetriever<HttpError> {

    private final String baseUrl;
    private final HttpClient httpClient;

    public HttpArtifactRetriever(String baseUrl,
                                 HttpClient httpClient) {
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
    }

    public HttpArtifactRetriever(String baseUrl) {
        this(baseUrl, DefaultHttpClient.get());
    }

    public HttpArtifactRetriever() {
        this(MavenUtils.MAVEN_CENTRAL_URL);
    }

    @Override
    public String getDescription() {
        return "http-repository[" + baseUrl + "]";
    }

    @Override
    public CompletionStage<ArtifactResolution<HttpError>> retrieve(Artifact artifact) {
        var requestPath = MavenUtils.standardArtifactPath(artifact, false);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(new URI(baseUrl + "/" + requestPath)).build();
        } catch (URISyntaxException e) {
            return completedStage(ArtifactResolution.failure(
                    new HttpError(artifact, this, Either.right(e))));
        }

        var requestTime = System.currentTimeMillis();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .handle((response, err) -> {
                    if (err != null) {
                        return ArtifactResolution.failure(
                                new HttpError(artifact, this, Either.right(err)));
                    }
                    if (response.statusCode() == 200) {
                        return ArtifactResolution.success(new ResolvedArtifact(response.body(), artifact, this, requestTime));
                    }
                    return ArtifactResolution.failure(new HttpError(artifact, this, Either.left(response)));
                });
    }
}
