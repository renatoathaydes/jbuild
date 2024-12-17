package tests;

import jbuild.cli.RpcMain;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class RpcMainTest {

    static final int port = 9099;
    static final String token = "my-token";
    static final CountDownLatch stopper = new CountDownLatch(1);
    static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(1))
            .executor(Executors.newSingleThreadExecutor())
            .build();
    private static final URI rpcEndpoint = URI.create("http://localhost:" + port + "/jbuild-rpc");

    @BeforeAll
    static void start() throws InterruptedException {
        var startWait = new CountDownLatch(1);
        new Thread(() -> {
            startWait.countDown();
            try {
                RpcMain.runHttpServer(port, token, false, stopper);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();

        if (!startWait.await(2, TimeUnit.SECONDS)) {
            throw new RuntimeException("timeout waiting for start up");
        }

        ensureHttpServerResponding();
    }

    @AfterAll
    static void stop() {
        stopper.countDown();
        client.executor().ifPresent(e -> ((ExecutorService) e).shutdown());
    }

    private static void ensureHttpServerResponding() {
        Throwable error = null;
        var tries = 0;
        while (tries < 5) {
            try {
                client.send(
                        HttpRequest.newBuilder(rpcEndpoint).build(),
                        HttpResponse.BodyHandlers.ofString(UTF_8));
                // success if no Exception!
                return;
            } catch (IOException e) {
                error = e;

                // retry
                tries++;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ex);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        throw new RuntimeException("Unable to connect with the server in time. Last error: " + error);
    }

    @Test
    void getMethodIsNotAccepted() throws IOException, InterruptedException {
        var response = client.send(
                HttpRequest.newBuilder(rpcEndpoint)
                        .header("Authorization", "Bearer " + token)
                        .build(),
                HttpResponse.BodyHandlers.ofString(UTF_8));
        assertThat(response.statusCode()).withFailMessage(response::toString).isEqualTo(405);
    }

    @Test
    void postMethodWithoutAuthorizationIsNotAccepted() throws IOException, InterruptedException {
        var client = HttpClient.newHttpClient();

        var rpcMessage = "<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>" + CalledInRpcMainTest.class.getName() + ".greeting</methodName>\n" +
                "    <params>\n" +
                "    </params>\n" +
                "</methodCall>";

        var response = client.send(
                HttpRequest.newBuilder(rpcEndpoint)
                        .POST(BodyPublishers.ofString(rpcMessage)).build(), HttpResponse.BodyHandlers.ofString(UTF_8));

        assertThat(response.statusCode()).withFailMessage(response::toString).isEqualTo(403);
    }

    @Test
    void canPostRpcMessages() throws IOException, InterruptedException {
        var client = HttpClient.newHttpClient();

        var rpcMessage = "<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>" + CalledInRpcMainTest.class.getName() + ".greeting</methodName>\n" +
                "    <params>\n" +
                "    </params>\n" +
                "</methodCall>";

        var response = client.send(HttpRequest.newBuilder(rpcEndpoint)
                .header("Authorization", "Bearer " + token)
                .POST(BodyPublishers.ofString(rpcMessage)).build(), HttpResponse.BodyHandlers.ofString(UTF_8));

        assertThat(response.statusCode()).withFailMessage(response::toString).isEqualTo(200);
        assertThat(response.body())
                .isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<methodResponse><params><param>" +
                        "<value><string>hello</string></value>" +
                        "</param></params></methodResponse>");
        assertThat(response.headers().firstValue("Content-Type")).isPresent().get()
                .isEqualTo("text/xml; charset=utf-8");
    }

    @Test
    void canPostRpcMessageWithVarargsArgument() throws IOException, InterruptedException {
        var rpcMessage = "<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>" + CalledInRpcMainTest.class.getName() + ".takeSomeArgs</methodName>\n" +
                "    <params>\n" +
                "<param><value><boolean>true</boolean></value></param>" +
                "<param><value><i4>32</i4></value></param>" +
                "<param><value><i4>8</i4></value></param>" +
                "<param><value><string>hi</string></value></param>" +
                "    </params>\n" +
                "</methodCall>";

        var response = client.send(HttpRequest.newBuilder(rpcEndpoint)
                .header("Authorization", "Bearer " + token)
                .POST(BodyPublishers.ofString(rpcMessage)).build(), HttpResponse.BodyHandlers.ofString(UTF_8));

        assertThat(response.statusCode()).withFailMessage(response::toString).isEqualTo(200);
        assertThat(response.body())
                .isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<methodResponse><params><param>" +
                        "<value><int>34</int></value>" + "</param></params></methodResponse>");
        assertThat(response.headers().firstValue("Content-Type")).isPresent().get()
                .isEqualTo("text/xml; charset=utf-8");
    }

    // Test class in hello.jar:
    // package hello;
    // public final class Hi {
    //    public String getMessage() { return "Hello world"; }
    //    public String sayHi(String name) { return "Hi " + name; }
    //}
    @Test
    void canRunClassUsingCustomClasspath() throws IOException, InterruptedException {
        var jarsDir = System.getProperty("tests.int-tests.resources");
        assertThat(jarsDir).withFailMessage("Set the system property 'tests.int-tests.resources'").isNotBlank();

        var jarsAbsoluteDir = new File(jarsDir).getAbsolutePath();

        // RpcMain#run(String classpath, Object[] constructorData, String className, String methodName, String... args)
        var rpcMessage = "<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>" + RpcMain.class.getName() + ".run</methodName>\n" +
                "    <params>\n" +
                "<param><value>" + jarsAbsoluteDir + "/hello.jar</value></param>" +
                "<param><value><array><data></data></array></value></param>" +
                "<param><value>hello.Hi</value></param>" +
                "<param><value>getMessage</value></param>" +
                "    </params>\n" +
                "</methodCall>";

        var response = client.send(HttpRequest.newBuilder(rpcEndpoint)
                .header("Authorization", "Bearer " + token)
                .POST(BodyPublishers.ofString(rpcMessage)).build(), HttpResponse.BodyHandlers.ofString(UTF_8));

        assertThat(response.statusCode()).withFailMessage(response::toString).isEqualTo(200);
        assertThat(response.body())
                .isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<methodResponse><params><param>" +
                        "<value><string>Hello world</string></value>" + "</param></params></methodResponse>");
        assertThat(response.headers().firstValue("Content-Type")).isPresent().get()
                .isEqualTo("text/xml; charset=utf-8");

        // RpcMain#run(String classpath, Object[] constructorData, String className, String methodName, String... args)
        rpcMessage = "<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>" + RpcMain.class.getName() + ".run</methodName>\n" +
                "    <params>\n" +
                "<param><value><string>" + jarsAbsoluteDir + "/hello.jar</string></value></param>" +
                "<param><value><array><data></data></array></value></param>" +
                "<param><value><string>hello.Hi</string></value></param>" +
                "<param><value><string>sayHi</string></value></param>" +
                "<param><value><array><data><value><string>Joe</string></value></data></array></value></param>" +
                "    </params>\n" +
                "</methodCall>";

        response = client.send(HttpRequest.newBuilder(rpcEndpoint)
                .header("Authorization", "Bearer " + token)
                .POST(BodyPublishers.ofString(rpcMessage)).build(), HttpResponse.BodyHandlers.ofString(UTF_8));

        assertThat(response.statusCode()).withFailMessage(response::toString).isEqualTo(200);
        assertThat(response.body())
                .isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<methodResponse><params><param>" +
                        "<value><string>Hi Joe</string></value>" + "</param></params></methodResponse>");
        assertThat(response.headers().firstValue("Content-Type")).isPresent().get()
                .isEqualTo("text/xml; charset=utf-8");
    }

}
