package jbuild.cli;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jbuild.errors.JBuildException;
import jbuild.java.tools.runner.JavaRunner;
import jbuild.java.tools.runner.RpcCaller;
import jbuild.log.JBuildLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;

public final class RpcMain {

    public static void main(String[] args) throws IOException {
        if (args.length != 0) {
            throw new JBuildException("Unexpected arguments provided", USER_INPUT);
        }
        new RpcMain().run(0, new CountDownLatch(1));
    }

    public void run(int port, CountDownLatch stopper) throws IOException {
        var threadId = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), runnable -> {
            var thread = new Thread(runnable, "jbuild-rpc-" + threadId.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });

        var server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.setExecutor(executor);
        System.out.println("" + server.getAddress().getPort());

        var rpcCaller = new RpcCaller(RpcMain.class.getName());
        var serverContext = new JBuildHttpHandler(rpcCaller, server);
        server.createContext("/jbuild", serverContext);

        server.start();

        try {
            stopper.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                server.stop(1);
            } finally {
                executor.shutdown();
            }
        }
    }

    public Object run(String className, String methodName, Object... args)
            throws ExecutionException, InterruptedException {
        var completion = new CompletableFuture<>();
        try {
            new JavaRunner().run(className, methodName, args);
        } catch (Throwable t) {
            completion.completeExceptionally(t);
        }
        return completion.get();
    }

    public int runJBuild(String... args) throws ExecutionException, InterruptedException {
        var completion = new CompletableFuture<Integer>();
        try {
            new Main(args, completion::complete, RpcMain::createLogger);
        } catch (Throwable t) {
            completion.completeExceptionally(t);
        }
        return completion.get();
    }

    @Override
    public String toString() {
        return "RpcMain";
    }

    private static JBuildLog createLogger(boolean verbose) {
        // the only output to System.out should be the RPC messages
        return new JBuildLog(System.err, verbose);
    }

    private static final class JBuildHttpHandler implements HttpHandler {

        private final RpcCaller rpcCaller;
        private final HttpServer server;

        public JBuildHttpHandler(RpcCaller rpcCaller, HttpServer server) {
            this.rpcCaller = rpcCaller;
            this.server = server;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            var out = new ByteArrayOutputStream(4096);
            var code = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    rpcCaller.call(exchange.getRequestBody(), out);
                } catch (Exception e) {
                    code = 500;
                    out.reset();
                    out.write(("<?xml version=\"1.0\"?><error>" + e + "</error>").getBytes(UTF_8));
                }
            } else if ("DELETE".equals(exchange.getRequestMethod())) {
                code = 200;
                out.reset();
                out.write("<?xml version=\"1.0\"?><ok></ok>".getBytes(UTF_8));
                server.stop(1);
            } else {
                code = 503;
                out.write("<?xml version=\"1.0\"?><error>Only POST requests are accepted</error>".getBytes(UTF_8));
            }
            send(exchange, code, out);
        }

        private void send(HttpExchange exchange, int statusCode, ByteArrayOutputStream body) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "text/xml; charset=utf-8");
            exchange.sendResponseHeaders(statusCode, body.size());
            exchange.getResponseBody().write(body.toByteArray());
        }
    }
}
