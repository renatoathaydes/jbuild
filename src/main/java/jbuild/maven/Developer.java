package jbuild.maven;

import jbuild.util.WritableXml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public final class Developer implements WritableXml {

    final String name;
    final String email;
    final Organization organization;

    public Developer(String name, String email, Organization organization) {
        this.name = name;
        this.email = email;
        this.organization = organization;
    }

    @Override
    public void addTo(Element element, Document document) {
        var dev = document.createElement("developer");
        dev.appendChild(document.createElement("name")).setTextContent(name);
        dev.appendChild(document.createElement("email")).setTextContent(email);
        organization.addTo(dev, document);
        element.appendChild(dev);
    }
}
