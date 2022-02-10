package jbuild.artifact.http;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactMetadata;
import jbuild.artifact.ArtifactResolution;
import jbuild.artifact.ArtifactRetriever;
import jbuild.artifact.ResolvedArtifact;
import jbuild.artifact.VersionRange;
import jbuild.errors.HttpError;
import jbuild.errors.JBuildException;
import jbuild.maven.MavenUtils;
import jbuild.util.Either;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedStage;
import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.maven.MavenUtils.standardArtifactPath;
import static jbuild.util.AsyncUtils.handlingAsync;

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
        if (VersionRange.isVersionRange(artifact.version)) {
            var range = VersionRange.parse(artifact.version);
            return retrieveFromVersionRange(artifact, range);
        }
        URI requestPath;
        try {
            requestPath = new URI(standardArtifactPath(artifact, false));
        } catch (URISyntaxException e) {
            return completedStage(ArtifactResolution.failure(new HttpError(artifact, this, Either.right(e))));
        }

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

    private CompletionStage<ArtifactResolution<HttpError>> retrieveFromVersionRange(
            Artifact artifact,
            VersionRange range) {
        return retrieveMetadata(artifact).thenComposeAsync(completion -> completion.map(
                meta -> range.selectLatest(meta.getVersions())
                        .map(version -> retrieve(artifact.withVersion(version)))
                        .orElseGet(() -> completedStage(ArtifactResolution.failure(new HttpError(
                                artifact, this,
                                Either.right(new JBuildException(
                                        "unsatisfiable version range: " + artifact.version +
                                                " (available versions: " +
                                                String.join(", ", meta.getVersions()) +
                                                ")",
                                        ACTION_ERROR)))))),
                err -> completedStage(ArtifactResolution.failure(err))));
    }

    @Override
    public CompletionStage<Either<? extends ArtifactMetadata, HttpError>> retrieveMetadata(Artifact artifact) {
        var requestUri = buildMetadataUri(baseUrl, artifact);
        var request = HttpRequest.newBuilder(requestUri).build();

        return handlingAsync(httpClient.sendAsync(request,
                HttpResponse.BodyHandlers.ofByteArray()
        ), (response, httpRequestError) -> {
            Throwable error = null;
            if (httpRequestError == null) {
                if (response.statusCode() == 200) {
                    try {
                        return completedStage(Either.left(MavenUtils.parseMavenMetadata(
                                new ByteArrayInputStream(response.body()))));
                    } catch (ParserConfigurationException | IOException | SAXException e) {
                        error = e;
                    }
                }
                if (error == null) {
                    return completedStage(Either.right(new HttpError(artifact, this, Either.left(response))));
                }
            } else {
                error = httpRequestError;
            }
            return completedStage(Either.right(new HttpError(artifact, this, Either.right(error))));
        });
    }

    private static URI buildMetadataUri(URI baseUri, Artifact artifact) {
        return baseUri.resolve(URI.create(
                artifact.groupId.replace('.', '/') + '/' +
                        artifact.artifactId +
                        "/maven-metadata.xml"));
    }

}
