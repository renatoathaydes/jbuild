package jbuild.artifact.http;

import jbuild.api.JBuildException;
import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactMetadata;
import jbuild.artifact.ArtifactResolution;
import jbuild.artifact.ArtifactRetriever;
import jbuild.artifact.ResolvedArtifact;
import jbuild.artifact.Version;
import jbuild.artifact.VersionRange;
import jbuild.errors.HttpError;
import jbuild.log.JBuildLog;
import jbuild.maven.ArtifactKey;
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
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import static java.util.concurrent.CompletableFuture.completedStage;
import static jbuild.api.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.maven.MavenUtils.standardArtifactPath;
import static jbuild.util.AsyncUtils.toCompletableFuture;
import static jbuild.util.AsyncUtils.withRetries;

public class HttpArtifactRetriever implements ArtifactRetriever<HttpError> {

    private static final Duration HTTP_REQUEST_RETRY_DELAY = Duration.ofSeconds(1);

    private final JBuildLog log;
    private final URI baseUrl;
    private final HttpClient httpClient;

    private final Map<Artifact, CompletableFuture<Either<? extends ArtifactMetadata, HttpError>>> metadataCache;
    private final Map<Artifact, CompletableFuture<ArtifactResolution<HttpError>>> artifactCache;

    public HttpArtifactRetriever(JBuildLog log,
                                 URI baseUrl,
                                 HttpClient httpClient) {
        this.log = log;
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        metadataCache = new ConcurrentHashMap<>();
        artifactCache = new ConcurrentHashMap<>();
    }

    public HttpArtifactRetriever(JBuildLog log, String baseUrl) {
        this(log, URI.create(baseUrl), DefaultHttpClient.get());
    }

    public HttpArtifactRetriever(JBuildLog log) {
        this(log, MavenUtils.MAVEN_CENTRAL_URL);
    }

    @Override
    public String getDescription() {
        return "http-repository[" + baseUrl + "]";
    }

    @Override
    public boolean isLocalFileRetriever() {
        return false;
    }

    @Override
    public CompletionStage<ArtifactResolution<HttpError>> retrieve(Artifact artifact) {
        if (VersionRange.isVersionRange(artifact.version)) {
            var range = VersionRange.parse(artifact.version);
            return retrieveFromVersionRange(artifact, range);
        }
        return artifactCache.computeIfAbsent(artifact, a -> toCompletableFuture(doRetrieve(a)));
    }

    private CompletionStage<ArtifactResolution<HttpError>> doRetrieve(Artifact artifact) {

        URI requestPath;
        try {
            requestPath = new URI(standardArtifactPath(artifact, false));
        } catch (URISyntaxException e) {
            return completedStage(ArtifactResolution.failure(new HttpError(artifact, this, Either.right(e))));
        }

        var request = HttpRequest.newBuilder(baseUrl.resolve(requestPath)).build();

        var requestTime = System.currentTimeMillis();

        return sendArtifactRequest(artifact, request, requestTime);
    }

    private CompletionStage<ArtifactResolution<HttpError>> sendArtifactRequest(
            Artifact artifact, HttpRequest request, long requestTime) {
        return send(request, (response, err) -> {
            if (err != null) {
                return completedStage(ArtifactResolution.failure(
                        new HttpError(artifact, this, Either.right(err))));
            }
            if (response.statusCode() == 200) {
                return completedStage(ArtifactResolution.success(
                        new ResolvedArtifact(response.body(), artifact, this, requestTime)));
            }
            return completedStage(ArtifactResolution.failure(
                    new HttpError(artifact, this, Either.left(response))));
        });
    }

    private CompletionStage<ArtifactResolution<HttpError>> retrieveFromVersionRange(
            Artifact artifact,
            VersionRange range) {
        return retrieveMetadata(artifact).thenComposeAsync(completion -> completion.map(
                meta -> range.selectLatest(meta.getVersions())
                        .map(version -> retrieveVersion(range, artifact, version))
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

    private CompletionStage<ArtifactResolution<HttpError>> retrieveVersion(
            VersionRange range, Artifact artifact, Version version) {
        log.verbosePrintln(() -> "Artifact " + ArtifactKey.of(artifact) + " with version range " + range +
                " being resolved with fixed version " + version);
        return retrieve(artifact.withVersion(version));
    }

    @Override
    public CompletionStage<Either<? extends ArtifactMetadata, HttpError>> retrieveMetadata(Artifact artifact) {
        return metadataCache.computeIfAbsent(artifact.forMetadata(), ignore -> toCompletableFuture(doRetrieveMetadata(artifact)));
    }

    public CompletionStage<Either<? extends ArtifactMetadata, HttpError>> doRetrieveMetadata(Artifact artifact) {
        var requestUri = buildMetadataUri(baseUrl, artifact);
        var request = HttpRequest.newBuilder(requestUri).build();
        return send(request, (response, httpRequestError) -> {
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

    private <U> CompletionStage<U> send(
            HttpRequest request,
            BiFunction<HttpResponse<byte[]>, Throwable, CompletionStage<U>> handle) {
        log.verbosePrintln(() -> "Artifact retriever sending HTTP request: " + request);
        var hasRetried = new AtomicBoolean(false);
        return withRetries(
                () -> httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((ok, err) -> {
                    if (err != null) {
                        if (hasRetried.compareAndSet(false, true)) {
                            log.verbosePrintln(() -> "HTTP Request resulted in error, will retry: " + err);
                        }
                    } else {
                        log.verbosePrintln(() -> "Received HTTP response: " + ok);
                    }
                }),
                1, HTTP_REQUEST_RETRY_DELAY, handle);
    }

    private static URI buildMetadataUri(URI baseUri, Artifact artifact) {
        return baseUri.resolve(URI.create(
                artifact.groupId.replace('.', '/') + '/' +
                        artifact.artifactId +
                        "/maven-metadata.xml"));
    }

    @Override
    public String toString() {
        return getDescription();
    }
}
