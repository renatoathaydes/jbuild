package tests;

import jbuild.cli.RpcMain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.concurrent.CountDownLatch;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class RpcMainTest {

    final int port = 9099;
    final CountDownLatch stopper = new CountDownLatch(1);

    @BeforeEach
    void start() {
        new Thread(() -> {
            try {
                new RpcMain().run(port, stopper);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    @AfterEach
    void stop() {
        stopper.countDown();
    }

    @Test
    void getMethodIsNotAccepted() throws IOException, InterruptedException {
        var client = HttpClient.newHttpClient();
        var response = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/jbuild-rpc")).build(),
                HttpResponse.BodyHandlers.ofString(UTF_8));
        assertThat(response.statusCode()).withFailMessage(response::toString).isEqualTo(503);
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

        var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/jbuild-rpc"))
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
        var client = HttpClient.newHttpClient();

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

        var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/jbuild-rpc"))
                .POST(BodyPublishers.ofString(rpcMessage)).build(), HttpResponse.BodyHandlers.ofString(UTF_8));

        assertThat(response.statusCode()).withFailMessage(response::toString).isEqualTo(200);
        assertThat(response.body())
                .isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<methodResponse><params><param>" +
                        "<value><int>34</int></value>" + "</param></params></methodResponse>");
        assertThat(response.headers().firstValue("Content-Type")).isPresent().get()
                .isEqualTo("text/xml; charset=utf-8");
    }

}
