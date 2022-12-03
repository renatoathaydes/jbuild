package jbuild.java.tools.runner;

import org.junit.jupiter.api.Test;

import java.util.List;

import static jbuild.java.TestHelper.assertXml;

public class RpcCallerTest {

    @Test
    void canRunSingleMethodCallWithTypeReceiver() throws Exception {
        var caller = new RpcCaller(null);
        var response = caller.call("<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>" + TestCallable.class.getName() + ".hello</methodName>\n" +
                "    <params>\n" +
                "    </params>\n" +
                "</methodCall>");

        var doc = response.toDocument();

        assertXml(doc, List.of("methodResponse", "params", "param", "value", "string"))
                .isEqualTo("hello");
    }

    @Test
    void canRunSingleMethodCallWithoutTypeReceiver() throws Exception {
        var caller = new RpcCaller(TestCallable.class.getName());
        var response = caller.call("<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>toString</methodName>\n" +
                "    <params>\n" +
                "    </params>\n" +
                "</methodCall>");

        var doc = response.toDocument();

        assertXml(doc, List.of("methodResponse", "params", "param", "value", "string"))
                .isEqualTo("TestCallable");
    }

    @Test
    void canRunMethodWithIntArguments() throws Exception {
        var caller = new RpcCaller(TestCallable.class.getName());
        var response = caller.call("<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>add</methodName>\n" +
                "    <params>\n" +
                "      <param><value><int>2</int></value></param>" +
                "      <param><value><i4>3</i4></value></param>" +
                "    </params>\n" +
                "</methodCall>");

        var doc = response.toDocument();

        assertXml(doc, List.of("methodResponse", "params", "param", "value", "int"))
                .isEqualTo("5");
    }

    @Test
    void canRunMethodWithIntAndStringArguments() throws Exception {
        var caller = new RpcCaller(TestCallable.class.getName());
        var response = caller.call("<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>add</methodName>\n" +
                "    <params>\n" +
                "      <param><value><int>42</int></value></param>" +
                "      <param><value><string>The answer is</string></value></param>" +
                "    </params>\n" +
                "</methodCall>");

        var doc = response.toDocument();

        assertXml(doc, List.of("methodResponse", "params", "param", "value", "string"))
                .isEqualTo("The answer is: 42");
    }
}
