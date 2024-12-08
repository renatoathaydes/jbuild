package jbuild.extension.runner;

import jbuild.api.JBuildException;
import jbuild.api.change.ChangeKind;
import jbuild.api.change.ChangeSet;
import jbuild.api.change.FileChange;
import jbuild.api.config.JbConfig;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.lang.reflect.Array;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static jbuild.util.XmlUtils.childNamed;
import static jbuild.util.XmlUtils.childrenNamed;
import static jbuild.util.XmlUtils.descendantOf;
import static jbuild.util.XmlUtils.elementChildren;
import static jbuild.util.XmlUtils.structMember;
import static jbuild.util.XmlUtils.structMemberValue;
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

    @SuppressWarnings("unchecked")
    static <T> T extractValue(Element value, Class<T> type) {
        var val = extractValue(value);
        if (type.isPrimitive()) {
            // this boxes primitive values if necessary
            return (T) val;
        }
        return type.cast(val);
    }

    /**
     * A value may be a text-node (e.g. {@code <value>foo</value>}) for a String, or a typed node
     * (e.g. {@code <value><string>foo</string></value>}) where the top-element describes the type of the value.
     *
     * @param value the {@code <value>} element
     * @return the Java value
     */
    static Object extractValue(Element value) {
        var elementChildren = elementChildren(value);
        if (elementChildren.isEmpty()) {
            // the value may have a single text child, check that.
            var child = value.getFirstChild();
            if (child != null && child.getNodeType() == Node.TEXT_NODE) {
                return child.getTextContent();
            }
            if (value.getNodeType() == Node.ELEMENT_NODE && value.getNodeValue() == null) {
                return "";
            }
            throw new JBuildException("Invalid XML-RPC Value (not a string or element): " + value,
                    JBuildException.ErrorCause.USER_INPUT);
        }
        if (elementChildren.size() > 1) {
            throw new JBuildException("Invalid XML-RPC Value (more than one child): " + value,
                    JBuildException.ErrorCause.USER_INPUT);
        }
        var node = elementChildren.get(0);
        var nodeType = node.getNodeType();
        if (nodeType == Node.TEXT_NODE) {
            return node.getTextContent();
        }
        if (nodeType != Node.ELEMENT_NODE) {
            throw new JBuildException("Invalid XML-RPC Value (not a string or element): " + node,
                    JBuildException.ErrorCause.USER_INPUT);
        }
        switch (node.getTagName()) {
            case "string":
                return node.getTextContent();
            case "int":
            case "i4":
                return Integer.parseInt(node.getTextContent());
            case "boolean":
                return "1".equals(node.getTextContent());
            case "double":
                return Double.parseDouble(node.getTextContent());
            case "array":
                return arrayValue(node);
            case "struct":
                return structValue(node);
            case "null":
                return null;
            default:
                throw new UnsupportedOperationException("value of type '" +
                        node.getTagName() + "' is not supported");
        }
    }

    private static Object typedArray(List<Element> values, String type) {
        switch (type) {
            case "string": {
                var result = new String[values.size()];
                for (int i = 0; i < result.length; i++) {
                    result[i] = extractValue(values.get(i), String.class);
                }
                return result;
            }
            case "int":
            case "i4": {
                var result = new int[values.size()];
                for (int i = 0; i < result.length; i++) {
                    result[i] = extractValue(values.get(i), int.class);
                }
                return result;
            }
            case "boolean": {
                var result = new boolean[values.size()];
                for (int i = 0; i < result.length; i++) {
                    result[i] = extractValue(values.get(i), boolean.class);
                }
                return result;
            }
            case "double": {
                var result = new double[values.size()];
                for (int i = 0; i < result.length; i++) {
                    result[i] = extractValue(values.get(i), double.class);
                }
                return result;
            }
            case "struct": {
                var structType = peekStructType(values);
                var result = Array.newInstance(structType, values.size());
                for (int i = 0; i < values.size(); i++) {
                    Array.set(result, i, extractValue(values.get(i), structType));
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

    static Object arrayValue(Element array) {
        var data = childNamed("data", array)
                .orElseThrow(() -> new IllegalArgumentException("Array missing data: " + array));
        var values = childrenNamed("value", data);
        if (values.isEmpty()) return new Object[0];
        return typedArray(values, peekType(values));
    }

    private static Object structValue(Element element) {
        var maybeType = peekStructType(element, false);
        if (maybeType.isEmpty()) {
            throw new IllegalStateException("Not a struct: " + element);
        }

        Class<?> type = maybeType.get().getKey();
        var members = maybeType.get().getValue();
        if (members.isEmpty()) {
            throw new IllegalArgumentException("invalid struct, expected at least one member: " + element);
        }
        if (JbConfig.class.equals(type)) {
            return JbConfigXmlDeserializer.from(members);
        }
        if (ChangeSet.class.equals(type)) {
            return changeSetFrom(members);
        }
        if (FileChange.class.equals(type)) {
            return fileChangeFrom(members);
        }

        throw new IllegalArgumentException("no struct Java type is known with members: " + members);
    }

    private static ChangeSet changeSetFrom(List<Element> members) {
        var inputChanges = structMemberValue("inputChanges", members)
                .map(e -> fileChangesFrom("inputChanges", e))
                .orElseThrow();
        var outputChanges = structMemberValue("outputChanges", members)
                .map(e -> fileChangesFrom("outputChanges", e))
                .orElseThrow(() -> new IllegalArgumentException("XML-RPC struct missing outputChanges member: " + members));
        return new ChangeSet(inputChanges, outputChanges);
    }

    private static FileChange[] fileChangesFrom(String name, Element element) {
        var data = descendantOf(element, "array", "data")
                .orElseThrow(() -> new IllegalArgumentException("in '" + name + "': expected array/data, not " + element));
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
        var path = textOf(structMemberValue("path", members));
        var kind = textOf(structMemberValue("kind", members));
        return new FileChange(path, ChangeKind.valueOf(kind.toUpperCase(Locale.ROOT)));
    }

    private static Class<?> peekStructType(List<Element> values) {
        Class<?> currentType = Object.class;
        for (var value : values) {
            var type = peekStructType(value, true);
            if (type.isEmpty()) break;
            return type.get().getKey();
        }
        return currentType;
    }

    /**
     * Guess the Java type of a struct and if found, return also its members.
     *
     * @param value      XML value
     * @param checkChild true if the struct is in the child of the given value,
     *                   false if the struct is the value itself.
     **/
    private static Optional<Map.Entry<Class<?>, List<Element>>> peekStructType(
            Element value,
            boolean checkChild) {
        Class<?> currentType = Object.class;
        List<Element> members;
        if (checkChild) {
            var struct = childNamed("struct", value);
            if (struct.isEmpty()) return Optional.empty();
            members = childrenNamed("member", struct.get());
        } else {
            members = childrenNamed("member", value);
        }
        if (structMember("module", members).isPresent()) {
            currentType = JbConfig.class;
        }
        if (structMember("inputChanges", members).isPresent()) {
            currentType = ChangeSet.class;
        }
        if (structMember("path", members).isPresent()) {
            currentType = FileChange.class;
        }
        return Optional.of(new AbstractMap.SimpleEntry<>(currentType, members));
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
