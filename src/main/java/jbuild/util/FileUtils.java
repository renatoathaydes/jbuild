package jbuild.util;

import jbuild.errors.CloseException;
import jbuild.errors.JBuildException;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;

import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;

public final class FileUtils {

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
        if (files == null) return new File[0];
        return files;
    }
}
