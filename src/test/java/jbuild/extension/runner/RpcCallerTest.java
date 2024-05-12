package jbuild.extension.runner;

import jbuild.java.TestHelper;
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
                .isEqualTo("TestCallable(log=null)");
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
    void canRunMethodWithArrayArgument() throws Exception {
        var caller = new RpcCaller(TestCallable.class.getName());
        var response = caller.call("<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>run</methodName>\n" +
                "    <params>\n" +
                "        <param><value><array><data>\n" +
                "        <value><string>hello</string></value>" +
                "        <value><string>world</string></value>" +
                "        </data></array></value>\n" +
                "      </param>" +
                "    </params>\n" +
                "</methodCall>");

        var doc = response.toDocument();

        assertXml(doc, List.of("methodResponse", "params")).isEqualTo("");
    }

    @Test
    void canRunMethodWithEmptyArrayArgument() throws Exception {
        var caller = new RpcCaller(TestCallable.class.getName());
        var response = caller.call("<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>run</methodName>\n" +
                "    <params>\n" +
                "        <param><value><array><data>\n" +
                "        </data></array></value>\n" +
                "      </param>" +
                "    </params>\n" +
                "</methodCall>");

        var doc = response.toDocument();

        assertXml(doc, List.of("methodResponse", "params")).isEqualTo("");
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

    @Test
    void canRunMethodWithUntypedStringArguments() throws Exception {
        var caller = new RpcCaller(TestCallable.class.getName());
        var response = caller.call("<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>add</methodName>\n" +
                "    <params>\n" +
                "      <param><value>100</value></param>" +
                "      <param><value>Hello there</value></param>" +
                "    </params>\n" +
                "</methodCall>");

        var doc = response.toDocument();

        assertXml(doc, List.of("methodResponse", "params", "param", "value", "string"))
                .isEqualTo("Hello there, 100");
    }

    @Test
    void canRunMethodWithUntypedEmptyStringArgument() throws Exception {
        var caller = new RpcCaller(TestCallable.class.getName());
        var response = caller.call("<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>add</methodName>\n" +
                "    <params>\n" +
                "      <param><value></value></param>" +
                "      <param><value></value></param>" +
                "    </params>\n" +
                "</methodCall>");

        var doc = response.toDocument();

        assertXml(doc, List.of("methodResponse", "params", "param", "value", "string"))
                .isEqualTo(", ");
    }

    @Test
    void canRunMethodWithNullArgument() throws Exception {
        var caller = new RpcCaller(TestCallable.class.getName());
        var response = caller.call("<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>add</methodName>\n" +
                "    <params>\n" +
                "      <param><value>hello</value></param>" +
                "      <param><value><null /></value></param>" +
                "    </params>\n" +
                "</methodCall>");

        var doc = response.toDocument();

        assertXml(doc, List.of("methodResponse", "params", "param", "value", "string"))
                .isEqualTo("null, hello");
    }

    @Test
    void canRunMethodWithVarargsArgument() throws Exception {
        var caller = new RpcCaller(TestCallable.class.getName());
        var response = caller.call("<?xml version=\"1.0\"?>\n" + "<methodCall>\n"
                + "    <methodName>varargs</methodName>\n" + "    <params>\n"
                + "      <param><value><double>1.23</double></value></param>" +
                "      <param><value><array><data>"
                + "        <value><string>arg1</string></value>" +
                "        <value><string>arg2</string></value>"
                + "        <value><string>arg3</string></value>" +
                "        </data></array></value>" + "      </param>"
                + "    </params>\n" + "</methodCall>");

        var doc = response.toDocument();

        assertXml(doc, List.of("methodResponse", "params", "param", "value", "string"))
                .isEqualTo("[arg1, arg2, arg3]: 1.23");
    }

    @Test
    void canRunMethodWithVarargsArgumentEmpty() throws Exception {
        var caller = new RpcCaller(TestCallable.class.getName());
        var response = caller.call("<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>varargs</methodName>\n" +
                "    <params>\n" +
                "      <param><value><double>3.14</double></value></param>" +
                "      <param><value><array><data>" +
                "      </data></array></value>" +
                "      </param>" +
                "    </params>\n" +
                "</methodCall>");

        var doc = response.toDocument();

        assertXml(doc, List.of("methodResponse", "params", "param", "value", "string"))
                .isEqualTo("[]: 3.14");
    }

    @Test
    void canRunMethodReturningLists() throws Exception {
        var caller = new RpcCaller(null);
        var response = caller.call("<?xml version=\"1.0\"?>\n" +
                "<methodCall>\n" +
                "    <methodName>" + TestCallable.class.getName() + ".getListOfObjects</methodName>\n" +
                "    <params>\n" +
                "    </params>\n" +
                "</methodCall>");

        var doc = response.toDocument();

        assertXml(doc, List.of("methodResponse", "params", "param", "value",
                "array", "data", new TestHelper.IndexedPath("value", 0), "string"))
                .isEqualTo("hello");

        assertXml(doc, List.of("methodResponse", "params", "param", "value",
                "array", "data", new TestHelper.IndexedPath("value", 1),
                "array", "data", new TestHelper.IndexedPath("value", 0), "string"))
                .isEqualTo("world");

        assertXml(doc, List.of("methodResponse", "params", "param", "value",
                "array", "data", new TestHelper.IndexedPath("value", 1),
                "array", "data", new TestHelper.IndexedPath("value", 1), "string"))
                .isEqualTo("!");
    }

}
