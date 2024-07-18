package jbuild.extension.runner;

import jbuild.api.JBuildException;
import jbuild.api.change.ChangeKind;
import jbuild.api.change.ChangeSet;
import jbuild.api.change.FileChange;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static jbuild.util.XmlUtils.childNamed;
import static jbuild.util.XmlUtils.childrenNamed;
import static jbuild.util.XmlUtils.structMember;
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
        return extractValue(value);
    }

    /**
     * A value may be a text-node (e.g. {@code <value>foo</value>}) for a String, or a typed node
     * (e.g. {@code <value><string>foo</string></value>}) where the top-element describes the type of the value.
     *
     * @param value the {@code <value>} element
     * @return the Java value
     */
    private static Object extractValue(Element value) {
        var node = value.getFirstChild();
        if (node == null) {
            if (value.getNodeType() == Node.TEXT_NODE) {
                return value.getTextContent();
            }
            if (value.getNodeType() == Node.ELEMENT_NODE && value.getNodeValue() == null) {
                return "";
            }
            throw new JBuildException("Invalid XML-RPC Value (not a string or element): " + value,
                    JBuildException.ErrorCause.USER_INPUT);
        }
        var nodeType = node.getNodeType();
        if (nodeType == Node.TEXT_NODE) {
            return node.getTextContent();
        }
        if (nodeType != Node.ELEMENT_NODE) {
            throw new JBuildException("Invalid XML-RPC Value (not a string or element): " + node,
                    JBuildException.ErrorCause.USER_INPUT);
        }
        var child = (Element) node;
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
            case "array":
                return arrayValue(child);
            case "struct":
                return structValue(child);
            case "null":
                return null;
            default:
                throw new UnsupportedOperationException("value of type '" +
                        child.getTagName() + "' is not supported");
        }
    }

    private static Object typedArray(List<Element> values, String type) {
        switch (type) {
            case "string": {
                var result = new String[values.size()];
                for (int i = 0; i < result.length; i++) {
                    result[i] = (String) extractValue(values.get(i));
                }
                return result;
            }
            case "int":
            case "i4": {
                var result = new int[values.size()];
                for (int i = 0; i < result.length; i++) {
                    result[i] = (int) extractValue(values.get(i));
                }
                return result;
            }
            case "boolean": {
                var result = new boolean[values.size()];
                for (int i = 0; i < result.length; i++) {
                    result[i] = (boolean) extractValue(values.get(i));
                }
                return result;
            }
            case "double": {
                var result = new double[values.size()];
                for (int i = 0; i < result.length; i++) {
                    result[i] = (double) extractValue(values.get(i));
                }
                return result;
            }
            case "struct": {
                var structType = peekStructType(values);
                var result = Array.newInstance(structType, values.size());
                for (int i = 0; i < values.size(); i++) {
                    Array.set(result, i, structType.cast(extractValue(values.get(i))));
                }
                return result;
            }
            case "":
            case "null":
            case "array": {
                var result = new Object[values.size()];
                for (int i = 0; i < result.length; i++) {
                    result[i] = extractValue(values.get(i));
                }
                return result;
            }
            default:
                throw new UnsupportedOperationException("value of type '" +
                        type + "' is not supported");
        }
    }

    private static Object arrayValue(Element array) {
        var data = childNamed("data", array)
                .orElseThrow(() -> new IllegalArgumentException("Array missing data: " + array));
        var values = childrenNamed("value", data);
        if (values.isEmpty()) return new Object[0];
        return typedArray(values, peekType(values));
    }

    private static Object structValue(Element element) {
        var members = childrenNamed("member", element);

        if (members.isEmpty()) {
            throw new IllegalArgumentException("invalid struct, expected at least one member: " + element);
        }

        var fields = members.stream()
                .map(e -> textOf(childNamed("name", e)))
                .collect(Collectors.toSet());

        if (fields.equals(Set.of("inputChanges", "outputChanges"))) {
            return changeSetFrom(members);
        } else if (fields.equals(Set.of("path", "kind"))) {
            return fileChangeFrom(members);
        } else {
            throw new IllegalArgumentException("no struct Java type is known with fields: " + fields + ": " + element);
        }
    }

    private static ChangeSet changeSetFrom(List<Element> members) {
        var inputChanges = structMember("inputChanges", members)
                .map(e -> fileChangesFrom("inputChanges", childNamed("value", e)
                        .orElseThrow(() -> new IllegalArgumentException("struct inputChanges member has no value"))))
                .orElseThrow();
        var outputChanges = structMember("outputChanges", members)
                .map(e -> fileChangesFrom("outputChanges", childNamed("value", e)
                        .orElseThrow(() -> new IllegalArgumentException("struct outputChanges member has no value"))))
                .orElseThrow();
        return new ChangeSet(inputChanges, outputChanges);
    }

    private static FileChange[] fileChangesFrom(String name, Element element) {
        var array = childNamed("array", element)
                .orElseThrow(() -> new IllegalArgumentException("in '" + name + "': expected array, not " + element));
        var data = childNamed("data", array)
                .orElseThrow(() -> new IllegalArgumentException("in '" + name + "/array': expected data, not " + element));
        var values = childrenNamed("value", data);
        var structs = values.stream()
                .map(e -> childNamed("struct", e).orElseThrow(() ->
                        new IllegalArgumentException("expected only structs in array of FileChange")));
        try {
            return structs.map(RpcMethodCall::structValue)
                    .map(s -> (FileChange) s)
                    .toArray(FileChange[]::new);
        } catch (Exception e) {
            throw new IllegalArgumentException("error in FileChange struct: " + e);
        }
    }

    private static FileChange fileChangeFrom(List<Element> members) {
        var path = structMember("path", members)
                .map(e -> textOf(childNamed("value", e)))
                .orElseThrow();
        var kind = structMember("kind", members)
                .map(e -> textOf(childNamed("value", e)))
                .orElseThrow();
        return new FileChange(path, ChangeKind.valueOf(kind.toUpperCase(Locale.ROOT)));
    }

    private static Class<?> peekStructType(List<Element> values) {
        Class<?> currentType = Object.class;
        for (var value : values) {
            var struct = childNamed("struct", value);
            if (struct.isEmpty()) break;
            var members = childrenNamed("member", struct.get());
            if (structMember("inputChanges", members).isPresent()) {
                return ChangeSet.class;
            }
            if (structMember("path", members).isPresent()) {
                return FileChange.class;
            }
        }
        return currentType;
    }

    /**
     * Peek the types of all elements in the values.
     * <p>
     * If they are all the same, the type is returned, otherwise an empty string is returned.
     *
     * @param values array values
     * @return the type of all elements, or empty if not a singular type
     */
    private static String peekType(List<Element> values) {
        String currentType = "";
        for (var value : values) {
            var type = peekType(value);
            if (currentType.isEmpty()) {
                currentType = type;
            } else if (!currentType.equals(type)) {
                return "";
            }
        }
        return currentType;
    }

    private static String peekType(Element value) {
        var childNode = value.getFirstChild();
        if (childNode == null) {
            if (value.getNodeType() == Node.TEXT_NODE ||
                    (value.getNodeType() == Node.ELEMENT_NODE && value.getNodeValue() == null)) {
                return "string";
            }
        } else if (childNode.getNodeType() == Node.TEXT_NODE) {
            return "string";
        } else if (childNode.getNodeType() == Node.ELEMENT_NODE) {
            return ((Element) childNode).getTagName();
        }
        throw new IllegalStateException("Unsupported Node type: " + childNode);
    }

    @Override
    public String toString() {
        return "RpcMethodCall{" +
                "className=" + getClassName() +
                ", methodName='" + getMethodName() + '\'' +
                ", parameters='" + toString(getParameters()) + '\'' +
                '}';
    }

    private static String toString(Object[] args) {
        // convert top-level Object[] to String
        for (int i = 0; i < args.length; i++) {
            if (args[i] != null && args[i].getClass().isArray()) {
                args[i] = toString((Object[]) args[i]);
            }
        }
        return Arrays.toString(args);
    }
}
