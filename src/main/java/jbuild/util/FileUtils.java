package jbuild.util;

import jbuild.api.JBuildException;
import jbuild.errors.CloseException;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static jbuild.api.JBuildException.ErrorCause.USER_INPUT;

public final class FileUtils {

    public static final FilenameFilter CLASS_FILES_FILTER = (dir, name) -> name.endsWith(".class");

    public static boolean ensureDirectoryExists(File dir) {
        return dir.isDirectory() || dir.mkdirs();
    }

    public static String withoutExtension(String path) {
        var dotIndex = path.lastIndexOf('.');
        if (dotIndex <= 0) return path;
        return path.substring(0, dotIndex);
    }

    public static Set<String> relativize(String dir, Set<String> paths) {
        return relativizeStream(dir, paths.stream()).collect(toSet());
    }

    public static Stream<String> relativizeStream(String dir, Stream<String> paths) {
        if (dir.equals(".") || dir.isBlank()) return paths;
        var root = dir.endsWith(File.separator) ? dir.substring(0, dir.length() - 1) : dir;
        return paths.map(path ->
                path.startsWith(File.separator) ? path : String.join(File.separator, root, path));
    }

    public static String relativize(String dir, String path) {
        if (dir.isEmpty() || dir.equals(".") || dir.equals("." + File.separatorChar)) return path;
        return Paths.get(dir).resolve(path).toString();
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
