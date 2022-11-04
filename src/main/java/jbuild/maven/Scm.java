package jbuild.maven;

import jbuild.util.WritableXml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.net.URI;

public final class Scm implements WritableXml {

    final URI connection;
    final URI developerConnection;
    final URI url;

    public Scm(URI connection, URI developerConnection, URI url) {
        this.connection = connection;
        this.developerConnection = developerConnection;
        this.url = url;
    }

    @Override
    public void addTo(Element element, Document document) {
        var scm = document.createElement("scm");
        scm.appendChild(document.createElement("connection"))
                .setTextContent(connection.toString());
        scm.appendChild(document.createElement("developerConnection"))
                .setTextContent(developerConnection.toString());
        scm.appendChild(document.createElement("url"))
                .setTextContent(url.toString());
        element.appendChild(scm);
    }

}
