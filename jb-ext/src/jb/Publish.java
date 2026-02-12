package jb;

import jbuild.api.JBuildException;
import jbuild.api.JBuildLogger;
import jbuild.api.JbTask;
import jbuild.api.JbTaskInfo;
import jbuild.api.TaskPhase;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.*;
import org.xml.sax.SAXException;

@JbTaskInfo(name = "publishAll",
        description = "Publish all jb artifacts to Maven Central.",
        phase = @TaskPhase(name = "publish", index = 520))
public class Publish implements JbTask {


    private final JBuildLogger logger;
    private final DocumentBuilderFactory xmlFactory;
    
    public Publish(JBuildLogger logger) {
        this.logger = logger;
        xmlFactory = DocumentBuilderFactory.newInstance();
            try {
                xmlFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            } catch (ParserConfigurationException e) {
                throw new RuntimeException("Cannot parse XML without feature: " +
                        XMLConstants.FEATURE_SECURE_PROCESSING);
            }
    }

    @Override
    public List<String> dependsOn() {
        return List.of("compile");
    }

    @Override
    public void run(String... args) throws IOException {
        var credentials = parseTokenXmlFile();
        var projects = List.of(".");
        for (var project : projects) {
            System.out.println("Publishing: " + project);
            var pb = new ProcessBuilder().command("zsh", "-c", "jb -p " + project + " publish :-m");
            var env = pb.environment();
            env.put("MAVEN_USER", credentials.getKey());
            env.put("MAVEN_PASSWORD", credentials.getValue());
            var proc = pb.inheritIO().start();
            try {
                if (proc.waitFor() != 0) {
                    throw new RuntimeException("jb publish command failed in project: " + project);
                }
            } catch (InterruptedException e) {
                System.out.println("jb command interrupted!");
                return;
            }
        }
    }

    private Map.Entry<String, String> parseTokenXmlFile() throws IOException {
        Document doc;
        try {
            var db = xmlFactory.newDocumentBuilder();
            var credentialsFile = new FileInputStream(".publish-token.xml");
            try (credentialsFile) {
                doc = db.parse(credentialsFile);
            }            
        } catch (ParserConfigurationException | SAXException e) {
            throw new RuntimeException("Could not process XML file", e);
        }

        Element server = childNamed("server", doc).orElseThrow(() -> xmlError("server"));
        Element un = childNamed("username", server).orElseThrow(() -> xmlError("server/username"));
        Element ps = childNamed("password", server).orElseThrow(() -> xmlError("server/password"));

        return Map.entry(un.getTextContent(), ps.getTextContent());
    }

    private static RuntimeException xmlError(String field) {
        return new RuntimeException("Cannot find field in XML: " + field);
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

}
