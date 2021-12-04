package jbuild.errors;

import jbuild.artifact.Artifact;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class HttpError implements ArtifactRetrievalError {

    private final Artifact artifact;
    public final HttpResponse<byte[]> httpResponse;

    public HttpError(Artifact artifact,
                     HttpResponse<byte[]> httpResponse) {
        this.artifact = artifact;
        this.httpResponse = httpResponse;
    }

    @Override
    public Artifact getArtifact() {
        return artifact;
    }

    @Override
    public void describe(StringBuilder builder, boolean verbose) {
        builder.append(artifact).append(" could not be fetched: http-status=")
                .append(httpResponse.statusCode())
                .append(verbose
                        ? ", http-body = " + new String(httpResponse.body(), StandardCharsets.UTF_8)
                        : "");
    }
}
