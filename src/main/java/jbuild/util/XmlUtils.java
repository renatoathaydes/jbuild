package jbuild.util;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class XmlUtils {

    public enum XmlSingletons {
        INSTANCE;

        @SuppressWarnings("ImmutableEnumChecker")
        public final DocumentBuilderFactory factory;

        XmlSingletons() {
            factory = DocumentBuilderFactory.newInstance();
            try {
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            } catch (ParserConfigurationException e) {
                throw new RuntimeException("Cannot parse XML without feature: " +
                        XMLConstants.FEATURE_SECURE_PROCESSING);
            }
        }

    }

    public static Optional<Element> descendantOf(Node node, String... names) {
        var current = node;
        for (var name : names) {
            var maybeCurrent = childNamed(name, current);
            if (maybeCurrent.isPresent()) {
                current = maybeCurrent.get();
            } else {
                return Optional.empty();
            }
        }
        if (current instanceof Element) {
            return Optional.of((Element) current);
        }
        return Optional.empty();
    }

    public static Optional<Element> childNamed(String name, Node node) {
        return childNamed(name, node, 0);
    }

    public static Optional<Element> childNamed(String name, Node node, int index) {
        var children = node.getChildNodes();
        var currentIndex = 0;
        for (var i = 0; i < children.getLength(); i++) {
            var child = children.item(i);
            if (child instanceof Element) {
                var elem = (Element) child;
                if (name.equals(elem.getTagName())) {
                    if (index == currentIndex) {
                        return Optional.of(elem);
                    }
                    currentIndex++;
                }
            }
        }
        return Optional.empty();
    }

    public static List<Element> childrenNamed(String name, Node node) {
        var result = new ArrayList<Element>();
        var children = node.getChildNodes();
        for (var i = 0; i < children.getLength(); i++) {
            var child = children.item(i);
            if (child instanceof Element) {
                var elem = (Element) child;
                if (name.equals(elem.getTagName())) {
                    result.add(elem);
                }
            }
        }
        return result;
    }

    public static List<Element> elementChildren(Node node) {
        var result = new ArrayList<Element>();
        var children = node.getChildNodes();
        for (var i = 0; i < children.getLength(); i++) {
            var child = children.item(i);
            if (child instanceof Element) {
                var elem = (Element) child;
                result.add(elem);
            }
        }
        return result;
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static String textOf(Optional<? extends Node> node) {
        return textOf(node, "");
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static String textOf(Optional<? extends Node> node, String defaultValue) {
        return node.flatMap(c -> Optional.ofNullable(c.getTextContent()))
                .map(String::trim)
                .orElse(defaultValue);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static String textOf(Optional<? extends Node> node, Supplier<String> defaultValue) {
        return node.flatMap(c -> Optional.ofNullable(c.getTextContent()))
                .map(String::trim)
                .orElseGet(defaultValue);
    }

    public static Optional<Element> structMember(String name, List<Element> members) {
        return members.stream()
                .filter(e -> textOf(childNamed("name", e)).equals(name))
                .findFirst();
    }

    public static Optional<Element> structMemberValue(String name, List<Element> members) {
        return structMember(name, members).flatMap(m -> childNamed("value", m));
    }

    public static void writeXml(Node doc,
                                OutputStream output,
                                boolean indent) throws TransformerException {
        var transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setAttribute("indent-number", 2);
        var transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, indent ? "yes" : "no");
        var source = new DOMSource(doc);
        var result = new StreamResult(output);
        transformer.transform(source, result);
    }
}
