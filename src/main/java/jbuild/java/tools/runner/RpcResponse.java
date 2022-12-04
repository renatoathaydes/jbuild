package jbuild.java.tools.runner;

import jbuild.errors.JBuildException;
import jbuild.errors.XmlWriterException;
import jbuild.util.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.Map;
import java.util.Objects;

import static jbuild.util.XmlUtils.writeXml;

public final class RpcResponse {

    private final Object result;
    private final Throwable fault;

    private RpcResponse(Object result, Throwable fault) {
        this.result = result;
        this.fault = fault;
    }

    public static RpcResponse success(Object result) {
        return new RpcResponse(result, null);
    }

    public static RpcResponse error(Throwable fault) {
        return new RpcResponse(null, Objects.requireNonNull(fault));
    }

    public boolean isError() {
        return fault != null;
    }

    public Document toDocument() {
        Document document;
        try {
            document = XmlUtils.XmlSingletons.INSTANCE.factory.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new XmlWriterException(e);
        }
        document.setXmlStandalone(true);
        var root = document.createElement("methodResponse");
        document.appendChild(root);
        if (fault == null) {
            addResult(document, root, result);
        } else {
            addFault(document, root, fault);
        }

        return document;
    }

    public void writeTo(OutputStream out) {
        try {
            writeXml(toDocument(), out, false);
        } catch (TransformerException e) {
            throw new XmlWriterException(e);
        }
    }

    private static void addResult(Document document, Element element, Object result) {
        var params = element.appendChild(document.createElement("params"));
        if (result == null) return;
        params.appendChild(document.createElement("param"))
                .appendChild(createValue(document, result));
    }

    private static void addFault(Document document, Element element, Throwable fault) {
        element.appendChild(document.createElement("fault"))
                .appendChild(document.createElement("value"))
                .appendChild(createStruct(document, Map.of(
                        "faultCode", codeOf(fault),
                        "faultString", faultString(fault)
                )));

    }

    private static Element createValue(Document document, Object result) {
        var value = document.createElement("value");
        if (result instanceof String) {
            value.appendChild(document.createElement("string")).setTextContent((String) result);
        } else if (result instanceof Boolean) {
            var b = (boolean) result;
            value.appendChild(document.createElement("boolean")).setTextContent(b ? "1" : "0");
        } else if (result instanceof Integer) {
            value.appendChild(document.createElement("int")).setTextContent(result.toString());
        } else if (result instanceof Double) {
            value.appendChild(document.createElement("double")).setTextContent(result.toString());
        } else if (result.getClass().isArray()) {
            addArrayValues(document, value.appendChild(document.createElement("array")), result);
        } else {
            throw new UnsupportedOperationException("Result of type " +
                    result.getClass().getName() + " is not supported");
        }
        return value;
    }

    private static void addArrayValues(Document document, Node array, Object value) {
        var data = array.appendChild(document.createElement("data"));
        var length = Array.getLength(value);
        for (int i = 0; i < length; i++) {
            data.appendChild(createValue(document, Array.get(value, i)));
        }
    }

    private static int codeOf(Throwable fault) {
        if (fault instanceof JBuildException) {
            return 2;
        }
        return 1;
    }

    private static String faultString(Throwable fault) {
        if (fault instanceof JBuildException) {
            return fault.getMessage();
        }
        return fault.toString();
    }

    private static Element createStruct(Document document, Map<String, Object> values) {
        var struct = document.createElement("struct");
        values.forEach((name, value) -> {
            var member = struct.appendChild(document.createElement("member"));
            member.appendChild(document.createElement("name")).setTextContent(name);
            member.appendChild(createValue(document, value));
        });
        return struct;
    }
}
