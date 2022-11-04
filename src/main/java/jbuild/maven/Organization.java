package jbuild.maven;

import jbuild.util.WritableXml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.net.URI;

public final class Organization implements WritableXml {
    final String name;
    final URI url;

    public Organization(String name, URI url) {
        this.name = name;
        this.url = url;
    }

    @Override
    public void addTo(Element element, Document document) {
        element.appendChild(document.createElement("organization")).setTextContent(name);
        element.appendChild(document.createElement("organizationUrl")).setTextContent(url.toString());
    }
}
