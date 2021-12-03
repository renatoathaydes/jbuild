package jbuild.util;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class XmlUtils {

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
        return node.flatMap(c -> Optional.ofNullable(c.getTextContent()))
                .orElse("");
    }
}
