package jbuild.util;

import jbuild.errors.CloseException;
import jbuild.errors.JBuildException;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.toList;
import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;

public final class FileUtils {

    public static final FilenameFilter CLASS_FILES_FILTER = (dir, name) -> name.endsWith(".class");

    public static boolean ensureDirectoryExists(File dir) {
        return dir.isDirectory() || dir.mkdirs();
    }

    public static CompletableFuture<byte[]> readAllBytes(Path file) {
        return readAllBytes(file, 4096);
    }

    public static CompletableFuture<byte[]> readAllBytes(Path file, int bufferLength) {
        if (!file.toFile().isFile()) {
            return CompletableFuture.failedFuture(new NoSuchFileException(file.toString()));
        }

        var completionStage = new CompletableFuture<byte[]>();
        var result = new byte[Math.toIntExact(file.toFile().length())];

        AsynchronousFileChannel channel;
        try {
            channel = AsynchronousFileChannel.open(file, StandardOpenOption.READ);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }

        var buffer = ByteBuffer.allocate(bufferLength);

        channel.read(buffer, 0L, 0, new CompletionHandler<>() {
            @Override
            public void completed(Integer bytesRead, Integer currentOffset) {
                if (bytesRead < 0) {
                    try {
                        channel.close();
                    } catch (IOException e) {
                        completionStage.completeExceptionally(new CloseException(e));
                        return;
                    }
                    completionStage.complete(result);
                } else {
                    buffer.flip();
                    buffer.get(result, currentOffset, bytesRead);
                    buffer.flip();
                    var nextOffset = currentOffset + bytesRead;
                    channel.read(buffer, nextOffset, nextOffset, this);
                }
            }

            @Override
            public void failed(Throwable exc, Integer attachment) {
                completionStage.completeExceptionally(exc);
            }
        });

        return completionStage;
    }

    public static File[] allFilesInDir(File directory, FileFilter filter) {
        if (!directory.isDirectory()) {
            throw new JBuildException("not a directory: " + directory, USER_INPUT);
        }
        var files = directory.listFiles(filter);
        if (files == null)
            return new File[0];
        return files;
    }

    public static List<FileCollection> collectFiles(Set<String> directories,
                                                    FilenameFilter filter) {
        return directories.stream()
                .map(dirPath -> collectFiles(dirPath, filter))
                .collect(toList());
    }

    public static FileCollection collectFiles(String dirPath,
                                              FilenameFilter filter) {
        var dir = new File(dirPath);
        if (dir.isDirectory()) {
            var children = dir.listFiles();
            if (children != null) {
                return new FileCollection(dirPath, Stream.of(children)
                        .flatMap(child -> fileOrChildDirectories(child, filter))
                        .collect(toList()));
            }
        }
        return new FileCollection(dirPath);
    }

    public static String replaceExtension(String path, String expectedExt, String newExt) {
        if (path.endsWith(expectedExt)) {
            return path.substring(0, path.length() - expectedExt.length()) + newExt;
        }
        return path;
    }

    // FIXME sort the entries in the jar as the jar tool does, add directory entries
    public static void patchJar(File jarFile,
                                String baseDir,
                                Set<String> add,
                                Set<String> delete) throws IOException {
        if (add.isEmpty() && delete.isEmpty()) return;
        var newFile = Files.createTempFile("jbuild-temp-jar-", ".jar").toFile();
        var newJar = new ZipOutputStream(new FileOutputStream(newFile));
        try (var jar = new ZipFile(jarFile, ZipFile.OPEN_READ)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (delete.contains(entry.getName())) continue;
                newJar.putNextEntry(entry);
                var entryStream = jar.getInputStream(entry);
                entryStream.transferTo(newJar);
                entryStream.close();
                newJar.closeEntry();
            }
            for (var toAdd : add) {
                var file = new File(baseDir, toAdd);
                try (var in = new FileInputStream(file)) {
                    newJar.putNextEntry(new ZipEntry(toAdd));
                    in.transferTo(newJar);
                    newJar.closeEntry();
                }
            }
        }
        newJar.close();
        if (!newFile.renameTo(jarFile)) {
            throw new IOException("unable to replace jar file with new contents: " + jarFile);
        }
    }

    private static Stream<String> fileOrChildDirectories(File file, FilenameFilter filter) {
        if (file.isFile() && filter.accept(file.getParentFile(), file.getName())) {
            return Stream.of(file.getPath());
        }
        if (file.isDirectory()) {
            var children = file.listFiles();
            if (children != null) {
                return Stream.of(children)
                        .flatMap(child -> fileOrChildDirectories(child, filter));
            }
        }
        return Stream.of();
    }

}
