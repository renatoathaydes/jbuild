package jbuild.maven;

import jbuild.artifact.Artifact;
import jbuild.errors.XmlWriterException;
import jbuild.util.WritableXml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;

public final class PublishableMavenPom implements WritableXml {

    final Artifact artifact;
    final String name;
    final String description;
    final URI url;
    final List<License> licenses;
    final List<Developer> developers;
    final Scm scm;
    final List<Dependency> dependencies;

    public PublishableMavenPom(Artifact artifact,
                               String name,
                               String description,
                               URI url,
                               List<License> licenses,
                               List<Developer> developers,
                               Scm scm,
                               List<Dependency> dependencies) {
        this.artifact = artifact;
        this.name = name;
        this.description = description;
        this.url = url;
        this.licenses = licenses;
        this.developers = developers;
        this.scm = scm;
        this.dependencies = dependencies;
    }

    /**
     * Writes this POM to the given output stream.
     *
     * @param out where to write
     * @throws XmlWriterException on any non-IO errors
     */
    public void writeTo(OutputStream out) {
        var factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new XmlWriterException(e);
        }
        var document = builder.newDocument();
        document.setXmlStandalone(true);
        var root = document.createElement("project");
        document.appendChild(root);
        addTo(root, document);
        try {
            writeXml(document, out);
        } catch (TransformerException e) {
            throw new XmlWriterException(e);
        }
    }

    // write doc to output stream
    @Override
    public void addTo(Element element, Document document) {
        element.appendChild(document.createElement("modelVersion")).setTextContent("4.0.0");
        artifact.addTo(element, document);
        element.appendChild(document.createElement("name")).setTextContent(name);
        element.appendChild(document.createElement("description")).setTextContent(description);
        element.appendChild(document.createElement("url")).setTextContent(url.toString());
        element.appendChild(appendChildren(document.createElement("licenses"), document, licenses));
        element.appendChild(appendChildren(document.createElement("developers"), document, developers));
        scm.addTo(element, document);
        element.appendChild(appendChildren(document.createElement("dependencies"), document, dependencies));
    }

    private static Element appendChildren(Element element,
                                          Document document,
                                          List<? extends WritableXml> children) {
        for (var child : children) {
            child.addTo(element, document);
        }
        return element;
    }

    private static void writeXml(Document doc,
                                 OutputStream output) throws TransformerException {
        var transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setAttribute("indent-number", 2);
        var transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        var source = new DOMSource(doc);
        var result = new StreamResult(output);
        transformer.transform(source, result);
    }

}
