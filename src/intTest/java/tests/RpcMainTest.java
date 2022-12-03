package tests;

import jbuild.cli.RpcMain;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class RpcMainTest {

    @Test
    void canRunRpcMain() {
        var firstCall = ("<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>toString</methodName>\n" +
                "    <params>\n" +
                "    </params>\n" +
                "</methodCall>"
        ).getBytes(UTF_8);

        var secondCall = ("<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>close</methodName>\n" +
                "    <params>\n" +
                "    </params>\n" +
                "</methodCall>"
        ).getBytes(UTF_8);

        var data = new byte[4 + firstCall.length + 4 + secondCall.length + 4];

        var lengthBuffer = ByteBuffer.allocate(4);
        lengthBuffer.mark();
        lengthBuffer.putInt(firstCall.length);
        System.arraycopy(lengthBuffer.array(), 0, data, 0, 4);

        lengthBuffer.reset();
        lengthBuffer.putInt(secondCall.length);
        System.arraycopy(lengthBuffer.array(), 0, data, 4 + firstCall.length, 4);

        System.arraycopy(firstCall, 0, data, 4, firstCall.length);
        System.arraycopy(secondCall, 0, data, 4 + firstCall.length + 4, secondCall.length);

        var input = new ByteArrayInputStream(data);
        var out = new ByteArrayOutputStream();

        RpcMain.main(input, out);

        assertThat(out.toString(UTF_8)).isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodResponse>" +
                "<params><param><value><string>RpcMain</string></value></param></params></methodResponse>\n" +
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodResponse><params/></methodResponse>\n");
    }
}
