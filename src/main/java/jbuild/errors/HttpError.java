package jbuild.errors;

import jbuild.artifact.Artifact;
import jbuild.artifact.ArtifactRetriever;

import java.net.http.HttpResponse;

public class HttpError<B> implements ArtifactRetrievalError {

    private final ArtifactRetriever<?> retriever;
    private final Artifact artifact;
    public final HttpResponse<B> httpResponse;

    public HttpError(ArtifactRetriever<?> retriever, Artifact artifact, HttpResponse<B> httpResponse) {
        this.retriever = retriever;
        this.artifact = artifact;
        this.httpResponse = httpResponse;
    }

    @Override
    public ArtifactRetriever<?> getRetriever() {
        return retriever;
    }

    @Override
    public Artifact getArtifact() {
        return artifact;
    }
}
