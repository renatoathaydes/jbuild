package jbuild.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class FileUtilsTest {

    @Test
    void canReadFileBytesNoBufferSpecified() throws Exception {
        var file = Files.createTempFile(FileUtilsTest.class.getSimpleName(), ".txt");
        var fileContents = "12345678901234567890";
        Files.writeString(file, fileContents, StandardCharsets.US_ASCII);

        var bytes = FileUtils.readAllBytes(file).get(5, TimeUnit.SECONDS);

        assertArrayEquals(fileContents.getBytes(StandardCharsets.US_ASCII), bytes);
    }

    @Test
    void canReadFileBytesWithTinyBufferLength() throws Exception {
        var file = Files.createTempFile(FileUtilsTest.class.getSimpleName(), ".txt");
        var fileContents = "12345678901234567890";
        Files.writeString(file, fileContents, StandardCharsets.US_ASCII);

        var bytes = FileUtils.readAllBytes(file, 4).get(5, TimeUnit.SECONDS);

        assertArrayEquals(fileContents.getBytes(StandardCharsets.US_ASCII), bytes);
    }

    @Test
    void canReadEmptyFile() throws Exception {
        var file = Files.createTempFile(FileUtilsTest.class.getSimpleName(), ".txt");

        var bytes = FileUtils.readAllBytes(file).get(5, TimeUnit.SECONDS);

        assertArrayEquals(new byte[]{}, bytes);
    }

    @Test
    void cannotReadFileThatDoesNotExist() throws Exception {
        var file = Paths.get("does", "not", "____exist____");

        var future = FileUtils.readAllBytes(file);

        assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
                .isInstanceOfAny(ExecutionException.class)
                .cause()
                .isInstanceOfAny(NoSuchFileException.class);
    }

    @Test
    void canRelativizePath() {
        final var sep = File.separator;
        assertThat(FileUtils.relativize("foo", "bar.zort"))
                .isEqualTo(String.join(sep, "foo", "bar.zort"));
        assertThat(FileUtils.relativize(sep + "foo", "bar.zort"))
                .isEqualTo(String.join(sep, sep + "foo", "bar.zort"));
        assertThat(FileUtils.relativize(sep + "foo", sep + "bar.zort"))
                .isEqualTo(String.join(sep, sep + "bar.zort"));
        assertThat(FileUtils.relativize("foo" + sep, "bar.zort"))
                .isEqualTo(String.join(sep, "foo", "bar.zort"));
        assertThat(FileUtils.relativize(sep + "foo" + sep, "bar" + File.separator + "zort.txt"))
                .isEqualTo(String.join(sep, sep + "foo", "bar", "zort.txt"));
    }

    @Test
    void canRelativizePaths() {
        final var sep = File.separator;
        assertThat(FileUtils.relativize("foo", Set.of("bar.zort", sep + "file.txt")))
                .isEqualTo(Set.of(String.join(sep, "foo", "bar.zort"), sep + "file.txt"));
    }

    @Test
    void canRemoveFileExtension() {
        assertThat(FileUtils.withoutExtension("")).isEqualTo("");
        assertThat(FileUtils.withoutExtension(".")).isEqualTo(".");
        assertThat(FileUtils.withoutExtension("a.")).isEqualTo("a");
        assertThat(FileUtils.withoutExtension("a.b")).isEqualTo("a");
        assertThat(FileUtils.withoutExtension("a.b.c")).isEqualTo("a.b");
        assertThat(FileUtils.withoutExtension("hello.txt")).isEqualTo("hello");
        assertThat(FileUtils.withoutExtension("a.b/c.d/foo.jar")).isEqualTo("a.b/c.d/foo");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void canCollectFilesInEmptyDir(boolean includeDirs) throws IOException {
        FilenameFilter filter = (File dir, String name) -> name.endsWith(".txt");
        var dir = Files.createTempDirectory(FileUtilsTest.class.getSimpleName());

        var result = FileUtils.collectFiles(dir.toString(), filter, includeDirs);

        assertThat(result.directory).isEqualTo(dir.toString());
        assertThat(result.files).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void canCollectFilesInDirWithFiles(boolean includeDirs) throws IOException {
        FilenameFilter filter = (File dir, String name) -> name.endsWith(".txt");
        var dir = Files.createTempDirectory(FileUtilsTest.class.getSimpleName());
        var dirPath = dir.toString();

        Files.writeString(dir.resolve("foo.txt"), "foo");
        Files.writeString(dir.resolve("foo.java"), "class foo {}");
        Files.writeString(dir.resolve("bar.txt"), "bar");
        Files.writeString(dir.resolve("car.txt"), "car");

        var result = FileUtils.collectFiles(dirPath, filter, includeDirs);

        assertThat(result.directory).isEqualTo(dirPath);
        assertThat(result.files).allMatch(file -> file.startsWith(dirPath + File.separatorChar));
        assertThat(result.files.stream().map(f -> f.substring(dirPath.length() + 1)))
                .containsExactlyInAnyOrder("bar.txt", "car.txt", "foo.txt");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void canCollectFilesInDirTree(boolean includeDirs) throws IOException {
        FilenameFilter filter = (File dir, String name) -> true;
        var dir = Files.createTempDirectory(FileUtilsTest.class.getSimpleName());
        var dirPath = dir.toString();

        var sub1 = dir.resolve("sub1");
        var sub2 = dir.resolve("sub2");
        var sub3 = sub2.resolve("sub3");
        var sub4 = sub3.resolve("sub4");

        assertThat(sub1.toFile().mkdir()).isTrue();
        assertThat(sub2.toFile().mkdir()).isTrue();
        assertThat(sub3.toFile().mkdir()).isTrue();
        assertThat(sub4.toFile().mkdir()).isTrue();

        Files.writeString(dir.resolve("foo.txt"), "foo");
        Files.writeString(dir.resolve("foo.java"), "class foo {}");
        Files.writeString(sub1.resolve("bar.txt"), "bar");
        Files.writeString(sub2.resolve("car.txt"), "car");
        Files.writeString(sub3.resolve("zort.txt"), "zort");

        var result = FileUtils.collectFiles(dirPath, filter, includeDirs);

        assertThat(result.directory).isEqualTo(dirPath);
        assertThat(result.files).allMatch(file -> file.startsWith(dirPath + File.separatorChar));
        if (includeDirs) {
            var sep = File.separatorChar;
            assertThat(result.files.stream().map(f -> f.substring(dirPath.length() + 1)))
                    .containsExactlyInAnyOrder("foo.txt", "foo.java",
                            "sub1" + sep, "sub2" + sep,
                            path("sub2", "sub3") + sep,
                            path("sub2", "sub3", "sub4") + sep,
                            path("sub1", "bar.txt"),
                            path("sub2", "car.txt"),
                            path("sub2", "sub3", "zort.txt"));
        } else {
            assertThat(result.files.stream().map(f -> f.substring(dirPath.length() + 1)))
                    .containsExactlyInAnyOrder("foo.txt", "foo.java",
                            path("sub1", "bar.txt"),
                            path("sub2", "car.txt"),
                            path("sub2", "sub3", "zort.txt"));
        }
    }

    private static String path(String path, String... paths) {
        return Paths.get(path, paths).toString();
    }
}
