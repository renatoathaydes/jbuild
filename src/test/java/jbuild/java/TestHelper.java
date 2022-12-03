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
import static jbuild.util.XmlUtils.childNamed;
import static jbuild.util.XmlUtils.writeXml;
import static org.assertj.core.api.Assertions.assertThat;

public final class TestHelper {

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

    public static AbstractStringAssert<?> assertXml(Node doc, List<String> path) {
        Node element = doc;
        var visited = new ArrayList<String>();
        for (var p : path) {
            visited.add(p);
            element = childNamed(p, element).orElseThrow(() -> {
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
}
