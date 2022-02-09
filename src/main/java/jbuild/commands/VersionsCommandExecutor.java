package jbuild.commands;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactMetadata;
import jbuild.artifact.http.DefaultHttpClient;
import jbuild.errors.JBuildException;
import jbuild.log.JBuildLog;
import jbuild.maven.MavenUtils;
import jbuild.util.Either;
import jbuild.util.NonEmptyCollection;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedStage;
import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.maven.MavenUtils.MAVEN_CENTRAL_URL;
import static jbuild.util.AsyncUtils.handlingAsync;

public final class VersionsCommandExecutor {

    private final JBuildLog log;
    private final NonEmptyCollection<URI> baseUrls;
    private final HttpClient httpClient;

    public VersionsCommandExecutor(JBuildLog log,
                                   NonEmptyCollection<URI> baseUrls,
                                   HttpClient httpClient) {
        this.log = log;
        this.baseUrls = baseUrls;
        this.httpClient = httpClient;
    }

    public VersionsCommandExecutor(JBuildLog log) {
        this(log, NonEmptyCollection.of(URI.create(MAVEN_CENTRAL_URL)), DefaultHttpClient.get());
    }

    public Map<Artifact, CompletionStage<Either<ArtifactMetadata, NonEmptyCollection<Throwable>>>> getVersions(
            Set<? extends Artifact> artifacts) {
        var results = new HashMap<
                Artifact,
                CompletionStage<Either<ArtifactMetadata, NonEmptyCollection<Throwable>>>>(artifacts.size());

        for (var artifact : artifacts) {
            var remainingRepos = baseUrls.iterator();
            results.put(artifact, fetchVersions(artifact, remainingRepos.next(), remainingRepos, List.of()));
        }

        return results;
    }

    private CompletionStage<Either<ArtifactMetadata, NonEmptyCollection<Throwable>>> fetchVersions(
            Artifact artifact,
            URI repository,
            Iterator<URI> remainingRepos,
            Iterable<Throwable> errors) {
        var requestUri = buildMetadataUri(repository, artifact);
        var request = HttpRequest.newBuilder(requestUri).build();

        log.verbosePrintln(() -> "Requesting metadata for " + artifact + " at " + requestUri);

        var requestTime = System.currentTimeMillis();

        return handlingAsync(httpClient.sendAsync(request,
                HttpResponse.BodyHandlers.ofByteArray()
        ), (response, httpRequestError) -> {
            Throwable error = null;
            if (httpRequestError == null) {
                if (response.statusCode() == 200) {
                    log.verbosePrintln(() -> artifact + " metadata retrieved successfully from " + requestUri +
                            " in " + (System.currentTimeMillis() - requestTime) + " ms");
                    try {
                        return completedStage(Either.left(MavenUtils.parseMavenMetadata(
                                new ByteArrayInputStream(response.body()))));
                    } catch (ParserConfigurationException | IOException | SAXException e) {
                        log.verbosePrintln(() -> "Problem parsing metadata for " + artifact + ": " + e);
                        error = e;
                    }
                }

                if (error == null) {
                    log.verbosePrintln(() -> "Unexpected server response for metadata of " +
                            artifact + ": " + response.statusCode());

                    error = new JBuildException("unexpected status code returned for metadata: " +
                            response.statusCode(), ACTION_ERROR);
                }
            } else {
                log.verbosePrintln(() -> "Problem requesting metadata for " + artifact + ": " + httpRequestError);
                error = httpRequestError;
            }

            if (remainingRepos.hasNext()) {
                return fetchVersions(artifact, remainingRepos.next(), remainingRepos,
                        NonEmptyCollection.of(errors, error));
            } else {
                log.verbosePrintln(() -> "No more repositories to attempt, cannot find versions of " + artifact);
            }

            return completedStage(Either.right(NonEmptyCollection.of(errors, error)));
        });
    }

    private static URI buildMetadataUri(URI baseUri, Artifact artifact) {
        return baseUri.resolve(URI.create(
                artifact.groupId.replace('.', '/') + '/' +
                        artifact.artifactId +
                        "/maven-metadata.xml"));
    }

}
