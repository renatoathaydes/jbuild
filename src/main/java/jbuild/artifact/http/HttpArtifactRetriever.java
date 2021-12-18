package jbuild.artifact.http;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactResolution;
import jbuild.artifact.ArtifactRetriever;
import jbuild.artifact.ResolvedArtifact;
import jbuild.errors.HttpError;
import jbuild.maven.MavenUtils;
import jbuild.util.Either;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletionStage;

import static jbuild.maven.MavenUtils.standardArtifactPath;

public class HttpArtifactRetriever implements ArtifactRetriever<HttpError> {

    private final URI baseUrl;
    private final HttpClient httpClient;

    public HttpArtifactRetriever(URI baseUrl,
                                 HttpClient httpClient) {
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
    }

    public HttpArtifactRetriever(String baseUrl) {
        this(URI.create(baseUrl), DefaultHttpClient.get());
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
        var requestPath = URI.create(standardArtifactPath(artifact, false));
        var request = HttpRequest.newBuilder(baseUrl.resolve(requestPath)).build();

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
