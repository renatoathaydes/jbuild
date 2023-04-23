package jbuild.util;

import jbuild.TestSystemProperties;
import jbuild.java.tools.Tools;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.stream.Collectors;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.assertj.core.api.Assertions.assertThat;

public class JarPatcherTest {

    @Test
    void canRemoveFileFromJar() throws IOException {
        var realJar = TestSystemProperties.jlineJar;
        var removedFile = "org/fusesource/jansi/Ansi.class";

        var jarTool = Tools.Jar.create();

        var modifiedRealJarContents = jarTool.listContents(realJar.getAbsolutePath()).getStdoutLines()
                .collect(Collectors.toList());

        // "patch" real jar contents to match the expected patched jar
        modifiedRealJarContents.remove(removedFile);

        var toPatch = Files.createTempFile(FileUtilsTest.class.getSimpleName(), ".jar").toFile();
        Files.copy(realJar.toPath(), toPatch.toPath(), REPLACE_EXISTING);

        JarPatcher.deleteFromJar(toPatch, Set.of(removedFile));

        var patchedJarContents = jarTool.listContents(toPatch.getAbsolutePath()).getStdoutLines()
                .collect(Collectors.toList());

        assertThat(patchedJarContents).containsExactlyElementsOf(modifiedRealJarContents);
    }

    @Test
    void canRemoveWholeDirFromJar() throws IOException {
        var realJar = TestSystemProperties.jlineJar;
        var removedFiles = Set.of(
                "org/fusesource/hawtjni/runtime/Callback.class",
                "org/fusesource/hawtjni/runtime/JNIEnv.class",
                "org/fusesource/hawtjni/runtime/Library.class",
                "org/fusesource/hawtjni/runtime/PointerMath.class"
        );

        var jarTool = Tools.Jar.create();

        var modifiedRealJarContents = jarTool.listContents(realJar.getAbsolutePath()).getStdoutLines()
                .collect(Collectors.toList());

        // "patch" real jar contents to match the expected patched jar
        modifiedRealJarContents.removeAll(removedFiles);

        // it needs to remove the empty directories as well, after removing all files in the directory
        modifiedRealJarContents.removeAll(Set.of("org/fusesource/hawtjni/", "org/fusesource/hawtjni/runtime/"));

        var toPatch = Files.createTempFile(FileUtilsTest.class.getSimpleName(), ".jar").toFile();
        Files.copy(realJar.toPath(), toPatch.toPath(), REPLACE_EXISTING);

        JarPatcher.deleteFromJar(toPatch, removedFiles);

        var patchedJarContents = jarTool.listContents(toPatch.getAbsolutePath()).getStdoutLines()
                .collect(Collectors.toList());

        assertThat(patchedJarContents).containsExactlyElementsOf(modifiedRealJarContents);
    }

}
