package jbuild.java.tools.runner;

import jbuild.errors.JBuildException;
import jbuild.util.XmlUtils;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;

public final class RpcCaller {

    private final String defaultReceiverClassName;
    private final JavaRunner runner = new JavaRunner();
    private final LengthBuffer lengthBuffer = new LengthBuffer();

    public RpcCaller(String defaultReceiverClassName) {
        this.defaultReceiverClassName = defaultReceiverClassName;
    }

    public void processStreams(InputStream input, OutputStream out) throws Exception {
        var chunkedInputStream = new ChunkedInputStream(input);
        var db = XmlUtils.XmlSingletons.INSTANCE.factory.newDocumentBuilder();
        var tempOut = new ByteArrayOutputStream(4096);
        RpcResponse response;
        do {
            response = call(db, chunkedInputStream);
            response.writeTo(tempOut);
            // the length of the response + new-line
            lengthBuffer.write(tempOut.size() + 1, out);
            tempOut.writeTo(out);
            tempOut.reset();
            // flushes stdout
            out.write('\n');
        } while (!response.isError() && chunkedInputStream.nextChunk());
    }

    public RpcResponse call(String message) throws Exception {
        var db = XmlUtils.XmlSingletons.INSTANCE.factory.newDocumentBuilder();
        var stream = new ByteArrayInputStream(message.getBytes(UTF_8));
        return call(db, stream);
    }

    private RpcResponse call(DocumentBuilder db, InputStream stream) {
        RpcMethodCall methodCall;
        try {
            methodCall = methodCall(db, stream);
        } catch (IOException | SAXException e) {
            throw new JBuildException("Error parsing RPC methodCall: " + e, USER_INPUT);
        }

        Object result;

        try {
            result = runner.run(methodCall);
        } catch (RuntimeException e) {
            var cause = e.getCause();
            return RpcResponse.error(cause == null ? e : cause);
        } catch (Exception e) {
            return RpcResponse.error(e);
        }

        try {
            return RpcResponse.success(result);
        } catch (Exception e) {
            throw new JBuildException("Error creating RPC response: " + e, ACTION_ERROR);
        }
    }

    private RpcMethodCall methodCall(DocumentBuilder db, InputStream stream) throws IOException, SAXException {
        return new RpcMethodCall(db.parse(stream), defaultReceiverClassName);
    }
}
