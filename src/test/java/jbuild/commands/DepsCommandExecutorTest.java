package jbuild.commands;

import jbuild.artifact.Artifact;
import jbuild.artifact.file.FileArtifactRetriever;
import jbuild.errors.FileRetrievalError;
import jbuild.log.JBuildLog;
import jbuild.maven.DependencyTree;
import jbuild.util.Either;
import jbuild.util.NonEmptyCollection;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static jbuild.util.AsyncUtils.awaitValues;
import static jbuild.util.CollectionUtils.mapValues;
import static org.assertj.core.api.Assertions.assertThat;

public class DepsCommandExecutorTest {

    private static final String repoDir = System.getProperty("tests.repo.dir");

    @Test
    void canHandleCircularDependencies() throws InterruptedException {
        var bytesOut = new ByteArrayOutputStream();
        var depsExecutor = createDepsCommand(bytesOut);

        var result = depsExecutor.fetchDependencyTree(
                Set.of(new Artifact("com.athaydes", "a", "1.0")),
                true);

        var queue = new LinkedBlockingDeque<Either<Map<Artifact, DependencyTree>, String>>(1);
        awaitValues(result).handle((ok, err) -> {
            if (err != null) return Either.right(err.toString());
            return queue.offer(unwrap(ok));
        });

        var res = queue.poll(5, TimeUnit.SECONDS);

        assert res != null : "Timeout waiting for result";

        var map = res.map(ok -> ok, Assertions::fail);

        assertThat(map).hasSize(1);
        assertThat(map.keySet()).isEqualTo(Set.of(new Artifact("com.athaydes", "a", "1.0", "pom")));

        var tree = map.get(new Artifact("com.athaydes", "a", "1.0", "pom"));
        assert tree != null;

        assertThat(tree.root.artifact).isEqualTo(new Artifact("com.athaydes", "a", "1.0", "pom"));
        assertThat(tree.dependencies).hasSize(1);
        assertThat(tree.dependencies.get(0).root.artifact).isEqualTo(new Artifact("com.athaydes", "b", "1.0", "pom"));
        assertThat(tree.dependencies.get(0).dependencies).hasSize(1);
        assertThat(tree.dependencies.get(0).dependencies.get(0).root.artifact)
                .isEqualTo(new Artifact("com.athaydes", "a", "1.0", "pom"));
        assertThat(tree.dependencies.get(0).dependencies.get(0).dependencies).hasSize(1);
        assertThat(tree.dependencies.get(0).dependencies.get(0).dependencies.get(0).root.artifact)
                .isEqualTo(new Artifact("com.athaydes", "b", "1.0", "pom"));
        assertThat(tree.dependencies.get(0).dependencies.get(0).dependencies.get(0).dependencies).isEmpty();

        assertThat(bytesOut.toString(StandardCharsets.UTF_8))
                .isEqualTo("WARNING: Detected circular dependency chain: com.athaydes:b:1.0 -> com.athaydes:a:1.0 -> com.athaydes:b:1.0\n");
    }

    private static DepsCommandExecutor<FileRetrievalError> createDepsCommand(OutputStream bytesOut) {
        var out = new PrintStream(bytesOut);
        var log = new JBuildLog(out, false);
        var retrievers = new FileArtifactRetriever(Path.of(repoDir));
        var fetcher = new FetchCommandExecutor<>(log, NonEmptyCollection.of(retrievers));
        var pomRetriever = new MavenPomRetriever<>(log, fetcher);
        return new DepsCommandExecutor<>(log, pomRetriever);
    }

    private static Either<Map<Artifact, DependencyTree>, String> unwrap(
            Map<Artifact, Either<Optional<DependencyTree>, Throwable>> result) {

        Map<Artifact, Either<DependencyTree, String>> map = mapValues(result, (value) -> value.map(
                ok -> ok.isEmpty() ? Either.right("Empty dependency tree") : Either.left(ok.get()),
                err -> Either.right("ERROR: " + err)));

        if (map.isEmpty()) {
            return Either.right("Map was empty");
        }

        var resultMap = new HashMap<Artifact, DependencyTree>();

        for (var entry : map.entrySet()) {
            var error = entry.getValue().map(ok -> null, err -> err);
            if (error != null) {
                return Either.right(error);
            } else {
                resultMap.put(entry.getKey(), entry.getValue().map(ok -> ok, Assertions::fail));
            }
        }

        return Either.left(resultMap);
    }
}