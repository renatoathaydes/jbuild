package jbuild.commands;

import jbuild.artifact.Artifact;
import jbuild.artifact.http.DefaultHttpClient;
import jbuild.errors.JBuildException;
import jbuild.log.JBuildLog;
import jbuild.maven.MavenMetadata;
import jbuild.maven.MavenUtils;
import jbuild.util.Either;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;

public class VersionsCommandExecutor {

    private final JBuildLog log;
    private final HttpClient httpClient;

    public VersionsCommandExecutor(JBuildLog log, HttpClient httpClient) {
        this.log = log;
        this.httpClient = httpClient;
    }

    public VersionsCommandExecutor(JBuildLog log) {
        this(log, DefaultHttpClient.get());
    }

    public Map<Artifact, CompletionStage<Either<MavenMetadata, Throwable>>> getVersions(
            Set<? extends Artifact> artifacts) {
        var baseUrl = MavenUtils.MAVEN_CENTRAL_URL;
        var results = new HashMap<Artifact, CompletionStage<Either<MavenMetadata, Throwable>>>(artifacts.size());

        for (var artifact : artifacts) {
            var requestUrl = baseUrl + '/' +
                    artifact.groupId.replace('.', '/') + '/' +
                    artifact.artifactId +
                    "/maven-metadata.xml";
            var request = HttpRequest.newBuilder(URI.create(requestUrl)).build();

            CompletionStage<Either<MavenMetadata, Throwable>> metadataCompletion = httpClient.sendAsync(request,
                    HttpResponse.BodyHandlers.ofByteArray()
            ).thenApply(response -> {
                if (response.statusCode() == 200) {
                    log.verbosePrintln(() -> artifact + " metadata retrieved successfully from " + baseUrl);
                    try {
                        return Either.left(MavenUtils.parseMavenMetadata(
                                new ByteArrayInputStream(response.body())));
                    } catch (ParserConfigurationException | IOException | SAXException e) {
                        log.verbosePrintln(() -> "Problem parsing metadata for " + artifact + ": " + e);
                        return Either.right(e);
                    }
                }
                return Either.right(new JBuildException("unexpected status code returned for metadata: " +
                        response.statusCode(), ACTION_ERROR));
            });

            results.put(artifact, metadataCompletion);
        }

        return results;
    }

}
