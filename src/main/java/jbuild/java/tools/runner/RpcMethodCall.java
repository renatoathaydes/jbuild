package jbuild.java.tools.runner;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Arrays;

import static jbuild.util.XmlUtils.childNamed;
import static jbuild.util.XmlUtils.childrenNamed;
import static jbuild.util.XmlUtils.textOf;

public final class RpcMethodCall {

    private final Element element;
    private final String defaultClass;

    public RpcMethodCall(Document document, String defaultClass) {
        element = childNamed("methodCall", document)
                .orElseThrow(() -> new IllegalArgumentException("Not a RPC Method Call XML document"));
        this.defaultClass = defaultClass;
    }

    public String getClassName() {
        var method = getMethod();
        var index = method.lastIndexOf('.');
        if (index < 0) return defaultClass;
        return method.substring(0, index);
    }

    public String getMethodName() {
        var method = getMethod();
        var index = method.lastIndexOf('.');
        if (index < 0) return method;
        return method.substring(index + 1);
    }

    public Object[] getParameters() {
        var params = childNamed("params", element);
        if (params.isEmpty()) return new Object[0];
        var paramList = childrenNamed("param", params.get());
        var result = new Object[paramList.size()];
        for (var i = 0; i < result.length; i++) {
            result[i] = paramValue(paramList.get(i));
        }
        return result;
    }

    private String getMethod() {
        return textOf(childNamed("methodName", element));
    }

    private static Object paramValue(Element element) {
        var value = childNamed("value", element)
                .orElseThrow(() -> new IllegalArgumentException("Parameter missing value: " + element));
        var child = (Element) value.getFirstChild();
        switch (child.getTagName()) {
            case "string":
                return child.getTextContent();
            case "int":
            case "i4":
                return Integer.parseInt(child.getTextContent());
            case "boolean":
                return "1".equals(child.getTextContent());
            case "double":
                return Double.parseDouble(child.getTextContent());
            default:
                throw new UnsupportedOperationException("parameter of type '" +
                        child.getTagName() + "' is not supported");
        }
    }

    @Override
    public String toString() {
        return "RpcMethodCall{" +
                "className=" + getClassName() +
                ", methodName='" + getMethodName() + '\'' +
                ", parameters='" + Arrays.toString(getParameters()) + '\'' +
                '}';
    }
}
