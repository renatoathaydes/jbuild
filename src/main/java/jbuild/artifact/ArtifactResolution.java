package jbuild.artifact;

import java.util.function.Consumer;
import java.util.function.Function;

public final class ArtifactResolution<Err> {

    private final ResolvedArtifact resolvedArtifact;
    private final Err error;

    private ArtifactResolution(ResolvedArtifact resolvedArtifact,
                               Err error) {
        // one and only one arg may be null
        if ((resolvedArtifact == null) == (error == null)) {
            throw new IllegalStateException("Expected one null of resolvedArtifact [" +
                    resolvedArtifact + "] and error [" + error + "]");
        }
        this.resolvedArtifact = resolvedArtifact;
        this.error = error;
    }

    public static <E> ArtifactResolution<E> success(ResolvedArtifact resolvedArtifact) {
        return new ArtifactResolution<>(resolvedArtifact, null);
    }

    public static <E> ArtifactResolution<E> failure(E error) {
        return new ArtifactResolution<>(null, error);
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
        if (resolvedArtifact != null) {
            useArtifact.accept(resolvedArtifact);
        } else {
            useError.accept(error);
        }
    }
}
