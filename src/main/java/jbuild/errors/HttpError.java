package jbuild.errors;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactRetriever;
import jbuild.util.Either;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class HttpError implements ArtifactRetrievalError {

    private final Artifact artifact;
    private final Either<HttpResponse<byte[]>, Throwable> errorReason;
    private final ArtifactRetriever<? extends HttpError> retriever;

    public HttpError(Artifact artifact,
                     ArtifactRetriever<? extends HttpError> retriever,
                     Either<HttpResponse<byte[]>, Throwable> errorReason) {
        this.artifact = artifact;
        this.retriever = retriever;
        this.errorReason = errorReason;
    }

    @Override
    public Artifact getArtifact() {
        return artifact;
    }

    @Override
    public void describe(StringBuilder builder, boolean verbose) {
        builder.append(artifact).append(" could not be fetched from ")
                .append(retriever.getDescription());

        errorReason.use(
                httpResponse -> builder.append(": http-status=")
                        .append(httpResponse.statusCode())
                        .append(verbose
                                ? httpResponse.statusCode() == 404
                                ? ", artifact does not exist"
                                : ", http-body = " + new String(httpResponse.body(), StandardCharsets.UTF_8)
                                : ""),
                throwable -> builder.append(" due to an error making a HTTP request: ")
                        .append(throwable));
    }
}
