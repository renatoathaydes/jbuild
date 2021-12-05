package jbuild.errors;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactRetriever;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class HttpError implements ArtifactRetrievalError {

    private final Artifact artifact;
    public final HttpResponse<byte[]> httpResponse;
    private final ArtifactRetriever<? extends HttpError> retriever;

    public HttpError(Artifact artifact,
                     ArtifactRetriever<? extends HttpError> retriever,
                     HttpResponse<byte[]> httpResponse) {
        this.artifact = artifact;
        this.retriever = retriever;
        this.httpResponse = httpResponse;
    }

    @Override
    public Artifact getArtifact() {
        return artifact;
    }

    @Override
    public void describe(StringBuilder builder, boolean verbose) {
        builder.append(artifact).append(" could not be fetched from ")
                .append(retriever.getDescription())
                .append(": http-status=")
                .append(httpResponse.statusCode())
                .append(verbose
                        ? httpResponse.statusCode() == 404
                        ? ", artifact does not exist"
                        : ", http-body = " + new String(httpResponse.body(), StandardCharsets.UTF_8)
                        : "");
    }
}
