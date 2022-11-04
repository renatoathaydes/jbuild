package jbuild.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public interface WritableXml {
    void addTo(Element element, Document document);
}
