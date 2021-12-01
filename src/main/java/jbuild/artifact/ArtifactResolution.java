package jbuild.artifact;

import java.util.function.Consumer;
import java.util.function.Function;

public final class ArtifactResolution<Err> {

    private final ResolvedArtifact resolvedArtifact;
    private final Err error;
    public final Artifact requestedArtifact;

    private ArtifactResolution(ResolvedArtifact resolvedArtifact,
                               Err error,
                               Artifact requestedArtifact) {
        // one and only one arg may be null
        if ((resolvedArtifact == null) == (error == null)) {
            throw new IllegalStateException("Expected one null of resolvedArtifact [" +
                    resolvedArtifact + "] and error [" + error + "]");
        }
        this.resolvedArtifact = resolvedArtifact;
        this.requestedArtifact = requestedArtifact;
        this.error = error;
    }

    public static <E> ArtifactResolution<E> success(ResolvedArtifact resolvedArtifact) {
        return new ArtifactResolution<>(resolvedArtifact, null, resolvedArtifact.artifact);
    }

    public static <E> ArtifactResolution<E> failure(E error, Artifact artifact) {
        return new ArtifactResolution<>(null, error, artifact);
    }

    public <T> T with(Function<ResolvedArtifact, T> withArtifact,
                      Function<Err, T> withError) {
        if (resolvedArtifact != null) {
            return withArtifact.apply(resolvedArtifact);
        }
        return withError.apply(error);
    }

    public void use(Consumer<ResolvedArtifact> useArtifact,
                    Consumer<Err> useError) {
        use(useArtifact, useError, null);
    }

    public void use(Consumer<ResolvedArtifact> useArtifact,
                    Consumer<Err> useError,
                    Runnable alwaysRun) {
        if (resolvedArtifact != null) {
            useArtifact.accept(resolvedArtifact);
        } else {
            useError.accept(error);
        }
        if (alwaysRun != null) alwaysRun.run();
    }
}
