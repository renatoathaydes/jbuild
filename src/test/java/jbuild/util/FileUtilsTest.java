package jbuild.util;

import org.junit.jupiter.api.Test;

import java.io.File;
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
}
