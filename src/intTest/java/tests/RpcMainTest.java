package tests;

import jbuild.cli.RpcMain;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class RpcMainTest {

    @Test
    void canRunRpcMain() {
        var firstCall = "<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>toString</methodName>\n" +
                "    <params>\n" +
                "    </params>\n" +
                "</methodCall>";

        var secondCall = "<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>close</methodName>\n" +
                "    <params>\n" +
                "    </params>\n" +
                "</methodCall>";

        var expectedResponse = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodResponse>" +
                "<params><param><value><string>RpcMain</string></value></param></params>" +
                "</methodResponse>\n";

        var expectedFinalResponse = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodResponse>" +
                "<params/></methodResponse>\n";

        var input = new ByteArrayInputStream(createRpcMessages(firstCall, secondCall));
        var out = new ByteArrayOutputStream();

        RpcMain.main(input, out);

        assertRpcResponses(out.toByteArray(), expectedResponse, expectedFinalResponse);
    }

    @Test
    void rpcMainCanInvokeAnotherClassBasicMethod() {
        var call = "<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>" + CalledInRpcMainTest.class.getName() + ".greeting</methodName>\n" +
                "    <params>\n" +
                "    </params>\n" +
                "</methodCall>";

        var expectedResponse = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodResponse>" +
                "<params><param><value><string>hello</string></value></param></params>" +
                "</methodResponse>\n";

        var input = new ByteArrayInputStream(createRpcMessages(call));
        var out = new ByteArrayOutputStream();

        RpcMain.main(input, out);

        assertRpcResponses(out.toByteArray(), expectedResponse);
    }

    @Test
    void rpcMainCanInvokeAnotherClassWithVarargsMissing() {
        var callNoVarargs = "<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>" + CalledInRpcMainTest.class.getName() + ".takeSomeArgs</methodName>\n" +
                "    <params>\n" +
                "      <param><value><boolean>1</boolean></value></param>" +
                "      <param><value><int>2</int></value></param>" +
                "    </params>\n" +
                "</methodCall>";

        var expectedResponse1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodResponse>" +
                "<params><param><value><int>3</int></value></param></params>" +
                "</methodResponse>\n";

        var input = new ByteArrayInputStream(createRpcMessages(callNoVarargs));
        var out = new ByteArrayOutputStream();

        RpcMain.main(input, out);

        assertRpcResponses(out.toByteArray(), expectedResponse1);
    }

    @Test
    void rpcMainCanInvokeAnotherClassWithVarargsExtra() {
        var callNoVarargs = "<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>" + CalledInRpcMainTest.class.getName() + ".takeSomeArgs</methodName>\n" +
                "    <params>\n" +
                "      <param><value><boolean>1</boolean></value></param>" +
                "      <param><value><int>2</int></value></param>" +
                "      <param><value><int>42</int></value></param>" +
                "      <param><value><string>hello world</string></value></param>" +
                "      <param><value><double>0.2</double></value></param>" +
                "    </params>\n" +
                "</methodCall>";

        var expectedResponse1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodResponse>" +
                "<params><param><value><int>6</int></value></param></params>" +
                "</methodResponse>\n";

        var input = new ByteArrayInputStream(createRpcMessages(callNoVarargs));
        var out = new ByteArrayOutputStream();

        RpcMain.main(input, out);

        assertRpcResponses(out.toByteArray(), expectedResponse1);
    }

    @Test
    void rpcMainCanInvokeAnotherClassWithVarargsExact() {
        var callNoVarargs = "<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>" + CalledInRpcMainTest.class.getName() + ".takeSomeArgs</methodName>\n" +
                "    <params>\n" +
                "      <param><value><boolean>0</boolean></value></param>" +
                "      <param><value><int>1</int></value></param>" +
                "      <param><value><double>0.2</double></value></param>" +
                "    </params>\n" +
                "</methodCall>";

        var expectedResponse1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodResponse>" +
                "<params><param><value><int>2</int></value></param></params>" +
                "</methodResponse>\n";

        var input = new ByteArrayInputStream(createRpcMessages(callNoVarargs));
        var out = new ByteArrayOutputStream();

        RpcMain.main(input, out);

        assertRpcResponses(out.toByteArray(), expectedResponse1);
    }

    @Test
    void rpcMainCanInvokeAnotherClassWithVarargArray() {
        var callNoVarargs = "<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>" + CalledInRpcMainTest.class.getName() + ".takeSomeArgs</methodName>\n" +
                "    <params>\n" +
                "      <param><value><boolean>0</boolean></value></param>" +
                "      <param><value><int>1</int></value></param>" +
                "      <param>" +
                "        <value><array><data>" +
                "          <value><double>0.2</double></value>" +
                "          <value><int>3</int></value>" +
                "        </data></array></value>" +
                "      </param>" +
                "    </params>\n" +
                "</methodCall>";

        var expectedResponse1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodResponse>" +
                "<params><param><value><int>3</int></value></param></params>" +
                "</methodResponse>\n";

        var input = new ByteArrayInputStream(createRpcMessages(callNoVarargs));
        var out = new ByteArrayOutputStream();

        RpcMain.main(input, out);

        assertRpcResponses(out.toByteArray(), expectedResponse1);
    }

    private static byte[] createRpcMessages(String... messages) {
        var data = new byte[(4 * messages.length) + Stream.of(messages).mapToInt(String::length).sum()];

        var lengthBuffer = ByteBuffer.allocate(4);
        lengthBuffer.mark();

        int destIndex = 0;
        for (var message : messages) {
            lengthBuffer.reset();
            lengthBuffer.putInt(message.length());
            System.arraycopy(lengthBuffer.array(), 0, data, destIndex, 4);
            destIndex += 4;
            var bytes = message.getBytes(UTF_8);
            System.arraycopy(bytes, 0, data, destIndex, bytes.length);
            destIndex += bytes.length;
        }

        return data;
    }

    private void assertRpcResponses(byte[] out, String... expectedResponses) {
        var expectedLength = (4 * expectedResponses.length) + Stream.of(expectedResponses).mapToInt(String::length).sum();
        assertThat(out.length)
                .withFailMessage(() -> "Length = " + out.length + " != " + expectedLength +
                        "\nFULL RESPONSE: " + new String(out, UTF_8))
                .isEqualTo(expectedLength);

        var lengthBuffer = ByteBuffer.allocate(4);
        lengthBuffer.mark();

        var index = 0;

        for (var response : expectedResponses) {
            lengthBuffer.reset();
            lengthBuffer.put(out, index, 4);
            assertThat(lengthBuffer.getInt(0))
                    .withFailMessage("expected response %s\nhas length %d != %d",
                            response, response.length(), lengthBuffer.getInt(0))
                    .isEqualTo(response.length());
            index += 4;
            assertThat(new String(out, index, response.length(), UTF_8)).isEqualTo(response);
            index += response.length();
        }
    }
}
