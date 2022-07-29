package jbuild.util;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class XmlUtils {

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
        var children = node.getChildNodes();
        for (var i = 0; i < children.getLength(); i++) {
            var child = children.item(i);
            if (child instanceof Element) {
                var elem = (Element) child;
                if (name.equals(elem.getTagName())) {
                    return Optional.of(elem);
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
}
