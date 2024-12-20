package jbuild.cli;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jbuild.api.JBuildException;
import jbuild.extension.runner.JavaRunner;
import jbuild.extension.runner.RpcCaller;
import jbuild.log.JBuildLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static jbuild.api.JBuildException.ErrorCause.USER_INPUT;
import static jbuild.util.TextUtils.isEither;

public final class RpcMain {

    private final JBuildLog log;
    private final JavaRunner javaRunner;

    public RpcMain() {
        this(new JBuildLog(System.out, false),
                new JavaRunner(new JBuildLog(System.out, false)));
    }

    public RpcMain(JBuildLog log) {
        this(log, new JavaRunner(log));
    }

    public RpcMain(JBuildLog log, JavaRunner javaRunner) {
        this.log = log;
        this.javaRunner = javaRunner;
    }

    public static void main(String[] args) throws IOException {
        var verbose = false;
        if (args.length == 1 && isEither(args[0], "-V", "--verbose")) {
            verbose = true;
        } else if (args.length != 0) {
            throw new JBuildException("Unexpected arguments provided", USER_INPUT);
        }

        // make sure to only serve RPC requests from the process that
        // started this server.
        var token = UUID.randomUUID().toString();

        RpcMain.runHttpServer(0, token, verbose, new CountDownLatch(1));
    }

    public static void runHttpServer(int port, String token, boolean verbose, CountDownLatch stopper) throws IOException {
        var threadId = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), runnable -> {
            var thread = new Thread(runnable, "jbuild-rpc-" + threadId.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });

        var server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.setExecutor(executor);

        // the caller must read the first 2 lines: PORT and TOKEN
        System.out.println(server.getAddress().getPort());
        System.out.println(token);

        var javaRunner = new JavaRunner(new JBuildLog(System.out, verbose));
        var serverContext = new JBuildHttpHandler(stopper, token, verbose, javaRunner);
        server.createContext("/jbuild", serverContext);

        server.start();

        var wasInterrupted = false;

        try {
            stopper.await();
        } catch (InterruptedException e) {
            wasInterrupted = true;
        } finally {
            try {
                server.stop(1);
            } finally {
                executor.shutdown();
            }
        }

        javaRunner.close();

        if (wasInterrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Run an arbitrary class and method.
     *
     * @param classpath       classpath to use
     * @param constructorData arguments for the constructor
     * @param className       name of the class
     * @param methodName      name of the method
     * @param args            method arguments
     * @return the result of the method call
     * @throws ExecutionException   when a problem occurs within an async call
     * @throws InterruptedException if the Thread is interrupted
     */
    public Object run(String classpath,
                      Object[] constructorData,
                      String className,
                      String methodName,
                      Object... args)
            throws ExecutionException, InterruptedException {
        return javaRunner.run(classpath, className, constructorData, methodName, args);
    }

    /**
     * This method is expected to be called via XML-RPC.
     *
     * @param args JBuild arguments
     * @return exit code
     * @throws ExecutionException   when a problem occurs within an async call
     * @throws InterruptedException if the Thread is interrupted
     */
    @SuppressWarnings("unused")
    public int runJBuild(String... args) throws ExecutionException, InterruptedException {
        var completion = new CompletableFuture<Integer>();
        try {
            new Main(args, completion::complete, (verbose) -> log);
        } catch (Throwable t) {
            completion.completeExceptionally(t);
        }
        return completion.get();
    }

    @Override
    public String toString() {
        return "RpcMain";
    }

    private static JBuildLog createLogger(boolean verbose, String prefix) {
        return new JBuildLog(System.out, verbose, prefix);
    }

    private static final class JBuildHttpHandler implements HttpHandler {

        private final CountDownLatch stopper;
        private final String token;
        private final boolean verbose;
        private final JavaRunner javaRunner;

        public JBuildHttpHandler(CountDownLatch stopper, String token, boolean verbose, JavaRunner javaRunner) {
            this.stopper = stopper;
            this.token = token;
            this.verbose = verbose;
            this.javaRunner = javaRunner;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            var requestHeaders = exchange.getRequestHeaders();
            var trackId = requestHeaders.getFirst("Track-Id");
            var logger = createLogger(verbose, trackId);
            logger.verbosePrintln(() -> "JBuild RPC received " + (trackId.isBlank()
                    ? "no Track-Id. Cannot associate logger messages with requests."
                    : "Track-Id: " + trackId));
            var rpcCaller = new RpcCaller(null, logger, javaRunner.withLog(logger));

            var out = new ByteArrayOutputStream(1024);
            var code = 200;
            if (!isGoodToken(requestHeaders.getFirst("Authorization"))) {
                code = 403;
                out.write("<?xml version=\"1.0\"?><error>Forbidden</error>".getBytes(UTF_8));
            } else if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    rpcCaller.call(exchange.getRequestBody(), out);
                } catch (Exception e) {
                    code = 500;
                    out.reset();
                    out.write(("<?xml version=\"1.0\"?><error>" + e + "</error>").getBytes(UTF_8));
                }
            } else if ("DELETE".equals(exchange.getRequestMethod())) {
                out.write("<?xml version=\"1.0\"?><ok></ok>".getBytes(UTF_8));
                stopper.countDown();
            } else {
                code = 405;
                out.write("<?xml version=\"1.0\"?><error>Method not allowed</error>".getBytes(UTF_8));
            }
            send(exchange, code, out);
        }

        private boolean isGoodToken(String authorizationHeader) {
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                return token.equals(authorizationHeader.substring("Bearer ".length()));
            }
            return false;
        }

        private void send(HttpExchange exchange, int statusCode, ByteArrayOutputStream body) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "text/xml; charset=utf-8");
            exchange.sendResponseHeaders(statusCode, body.size());
            exchange.getResponseBody().write(body.toByteArray());
            exchange.close();
        }
    }
}
