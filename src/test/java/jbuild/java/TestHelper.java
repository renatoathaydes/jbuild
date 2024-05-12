package jbuild.java;

import org.assertj.core.api.AbstractStringAssert;
import org.w3c.dom.Node;

import javax.xml.transform.TransformerException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static jbuild.util.XmlUtils.childNamed;
import static jbuild.util.XmlUtils.writeXml;
import static org.assertj.core.api.Assertions.assertThat;

public final class TestHelper {

    public interface PathComponent {
    }

    public static final class SimplePath implements PathComponent {
        public final String path;

        public SimplePath(String path) {
            this.path = path;
        }
    }

    public static final class IndexedPath implements PathComponent {
        public final String path;
        public final int index;

        public IndexedPath(String path, int index) {
            this.path = path;
            this.index = index;
        }
    }

    public static File file(String path) {
        return new File(path);
    }

    public static Jar jar(String path) {
        return jar(file(path));
    }

    public static Jar jar(File file) {
        return new Jar(file, Set.of(), () -> {
            throw new UnsupportedOperationException("cannot load test jar");
        });
    }

    public static AbstractStringAssert<?> assertXml(Node doc, List<?> path) {
        return assertXmlPath(doc, path.stream().map(TestHelper::convertPath).collect(toList()));
    }

    private static AbstractStringAssert<?> assertXmlPath(Node doc, List<? extends PathComponent> path) {
        Node element = doc;
        Integer index = null;
        var visited = new ArrayList<String>();
        for (var p : path) {
            String pathPart;
            if (p instanceof SimplePath) {
                pathPart = ((SimplePath) p).path;
                visited.add(pathPart);
            } else {
                assert p instanceof IndexedPath;
                pathPart = ((IndexedPath) p).path;
                index = ((IndexedPath) p).index;
                visited.add(pathPart + "[" + index + "]");
            }
            var child = index == null
                    ? childNamed(pathPart, element)
                    : childNamed(pathPart, element, index);
            index = null;
            element = child.orElseThrow(() -> {
                var docString = new ByteArrayOutputStream();
                try {
                    writeXml(doc, docString, true);
                } catch (TransformerException e) {
                    throw new RuntimeException(e);
                }
                return new AssertionError("No child found at: " +
                        String.join("/", visited) + "\nResult: " + docString.toString(UTF_8));
            });
        }
        return assertThat(element.getTextContent());
    }

    private static PathComponent convertPath(Object path) {
        if (path instanceof PathComponent) return (PathComponent) path;
        if (path instanceof String) {
            return new SimplePath((String) path);
        }
        throw new IllegalArgumentException("Unsupported path: " + path);
    }
}
