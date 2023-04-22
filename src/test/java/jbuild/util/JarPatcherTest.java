package jbuild.util;

import jbuild.TestSystemProperties;
import jbuild.java.tools.Tools;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.assertj.core.api.Assertions.assertThat;

public class JarPatcherTest {

    @Test
    void canPatchJarFile() throws IOException {
        var tempDir = Files.createTempDirectory(FileUtilsTest.class.getSimpleName());
        var srcDir = tempDir.resolve("src");
        assert srcDir.toFile().mkdir();
        var addedFile1 = srcDir.resolve("file1.txt");
        var addedFile2 = srcDir.resolve("file2.txt");
        Files.write(addedFile1, List.of("hello world"));
        Files.write(addedFile2, List.of("bye world"));

        var removedFile = "org/fusesource/jansi/Ansi.class";

        var realJar = TestSystemProperties.jlineJar;
        var toPatch = Files.createTempFile(FileUtilsTest.class.getSimpleName(), ".jar").toFile();
        Files.copy(realJar.toPath(), toPatch.toPath(), REPLACE_EXISTING);

        JarPatcher.patchJar(toPatch,
                tempDir.toAbsolutePath().toString(),
                Set.of("src/file1.txt", "src/file2.txt"),
                Set.of(removedFile));

        var jarTool = Tools.Jar.create();

        var modifiedRealJarContents = jarTool.listContents(realJar.getAbsolutePath()).getStdoutLines()
                .collect(Collectors.toList());

        // "patch" real jar contents to match the expected patched jar
        modifiedRealJarContents.add("src/");
        modifiedRealJarContents.add("src/file1.txt");
        modifiedRealJarContents.add("src/file2.txt");
        modifiedRealJarContents.remove(removedFile);

        var patchedJarContents = jarTool.listContents(toPatch.getAbsolutePath()).getStdoutLines()
                .collect(Collectors.toList());

        assertThat(patchedJarContents).containsExactlyInAnyOrderElementsOf(modifiedRealJarContents);
        assertThat(patchedJarContents).startsWith("META-INF/", "META-INF/MANIFEST.MF");
        assertThat(patchedJarContents).endsWith("src/", "src/file1.txt", "src/file2.txt");
        assertThat(contentsOfZipEntry("src/file1.txt", toPatch)).isEqualTo("hello world" + System.lineSeparator());
        assertThat(contentsOfZipEntry("src/file2.txt", toPatch)).isEqualTo("bye world" + System.lineSeparator());
    }

    private static String contentsOfZipEntry(String entry, File zipFile) throws IOException {
        try (var zip = new ZipFile(zipFile)) {
            var e = zip.getEntry(entry);
            return new String(zip.getInputStream(e).readAllBytes());
        }
    }

}
