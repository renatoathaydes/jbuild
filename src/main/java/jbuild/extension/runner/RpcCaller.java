package jbuild.extension.runner;

import jbuild.api.JBuildException;
import jbuild.log.JBuildLog;
import jbuild.util.XmlUtils;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static jbuild.api.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.api.JBuildException.ErrorCause.USER_INPUT;

public final class RpcCaller {

    private final String defaultReceiverClassName;
    private final JBuildLog log;
    private final JavaRunner runner;

    public RpcCaller(String defaultReceiverClassName) {
        this(defaultReceiverClassName, new JBuildLog(System.out, false));
    }

    public RpcCaller(String defaultReceiverClassName,
                     JBuildLog log) {
        this(defaultReceiverClassName, log, new JavaRunner(log));
    }

    public RpcCaller(String defaultReceiverClassName,
                     JBuildLog log,
                     JavaRunner javaRunner) {
        this.defaultReceiverClassName = defaultReceiverClassName;
        this.log = log;
        this.runner = javaRunner;
    }

    public void call(InputStream input, OutputStream output) throws Exception {
        var db = XmlUtils.XmlSingletons.INSTANCE.factory.newDocumentBuilder();
        var response = call(db, input);
        response.writeTo(output);
    }

    public RpcResponse call(String message) throws Exception {
        var db = XmlUtils.XmlSingletons.INSTANCE.factory.newDocumentBuilder();
        var stream = new ByteArrayInputStream(message.getBytes(UTF_8));
        return call(db, stream);
    }

    private RpcResponse call(DocumentBuilder db, InputStream stream) {
        var startTime = System.currentTimeMillis();
        RpcMethodCall methodCall;
        try {
            methodCall = methodCall(db, stream);
        } catch (IOException | SAXException e) {
            throw new JBuildException("Error parsing RPC methodCall: " + e, USER_INPUT);
        }

        if (log.isVerbose()) {
            log.verbosePrintln("Parsed RPC Method call in " + (System.currentTimeMillis() - startTime) + "ms - " + methodCall);
            startTime = System.currentTimeMillis();
        }

        Object result = null;
        Exception error = null;

        try {
            result = runner.run(methodCall);
        } catch (RuntimeException e) {
            error = e;
            var cause = e.getCause();
            return RpcResponse.error(cause == null ? e : cause);
        } catch (Exception e) {
            error = e;
            return RpcResponse.error(e);
        } finally {
            if (log.isVerbose()) {
                log.verbosePrintln("RPC Method call completed in " + (System.currentTimeMillis() - startTime) + "ms - " +
                        (error == null ? result : error));
                if (error != null) {
                    error.printStackTrace(log.out);
                }
            }
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
