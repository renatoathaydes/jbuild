package jbuild.cli;

import jbuild.errors.JBuildException;
import jbuild.java.tools.runner.RpcCaller;
import jbuild.log.JBuildLog;

import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;

public final class RpcMain implements Closeable, AutoCloseable {

    public static void main(String[] args) {
        main(System.in, System.out, args);
    }

    public static void main(InputStream input, OutputStream out, String... args) {
        if (args.length != 0) {
            throw new JBuildException("Unexpected arguments provided", USER_INPUT);
        }
        var rpcCaller = new RpcCaller(RpcMain.class.getName());

        try {
            rpcCaller.processStreams(input, out);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int run(String... args) throws ExecutionException, InterruptedException {
        var completion = new CompletableFuture<Integer>();
        try {
            new Main(args, completion::complete, RpcMain::createLogger);
        } catch (Throwable t) {
            completion.completeExceptionally(t);
        }
        return completion.get();
    }

    @Override
    public void close() {
        // currently, nothing to close
    }

    @Override
    public String toString() {
        return "RpcMain";
    }

    private static JBuildLog createLogger(boolean verbose) {
        // the only output to System.out should be the RPC messages
        return new JBuildLog(System.err, verbose);
    }
}
