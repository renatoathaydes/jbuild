package jbuild.extension.runner;

import jbuild.api.change.ChangeKind;
import jbuild.api.change.ChangeSet;
import jbuild.api.change.FileChange;
import jbuild.api.config.JbConfig;
import jbuild.java.TestHelper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
                .isEqualTo(new TestCallable().toString());
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

        assertXml(doc, List.of("methodResponse", "params"))
                .isEqualTo(Arrays.toString(new String[]{"hello", "world"}));
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

        assertXml(doc, List.of("methodResponse", "params")).isEqualTo("[]");
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

    @Test
    void canRunMethodTakingStructArg_ChangeSet() throws Exception {
        var caller = new RpcCaller(TestCallable.class.getName());
        var response = caller.call("<?xml version=\"1.0\"?>\n" +
                "<methodCall>" +
                "  <methodName>run</methodName>" +
                "  <params>" +
                "    <param><value>build/example-extension.jar</value></param>" +
                "    <param><value><array>" +
                "      <data><value><null /></value></data>" +
                "      </array></value>" +
                "    </param>" +
                "    <param><value>CopierTask</value></param>" +
                "    <param><value>run</value></param>" +
                "    <param><value><array><data><value><struct><member>" +
                "      <name>inputChanges</name><value><array><data><value>" +
                "        <struct>" +
                "          <member><name>path</name><value>input-resources</value></member>" +
                "          <member><name>kind</name><value>modified</value></member>" +
                "        </struct></value><value>" +
                "        <struct>" +
                "           <member><name>path</name><value>input-resources/new.txt</value></member>" +
                "           <member><name>kind</name><value>added</value></member>" +
                "        </struct></value></data></array>" +
                "      </value></member><member><name>outputChanges</name><value><array><data></data></array></value>" +
                "      </member></struct></value></data></array></value></param>" +
                "  </params>" +
                "</methodCall>");

        var doc = response.toDocument();

        assertXml(doc, List.of("methodResponse", "params", "param", "value", "string"))
                .isEqualTo("build/example-extension.jar:" +
                        Arrays.toString(new Object[]{null}) + ':' +
                        "CopierTask:" +
                        "run:" +
                        Arrays.toString(new Object[]{
                                new ChangeSet(
                                        new FileChange[]{
                                                new FileChange("input-resources", ChangeKind.MODIFIED),
                                                new FileChange("input-resources/new.txt", ChangeKind.ADDED),
                                        },
                                        new FileChange[0]
                                )
                        }));
    }

    @Test
    void canRunMethodTakingStructArg_JbConfig() throws Exception {
        var caller = new RpcCaller(TestCallable.class.getName());
        var response = caller.call("<?xml version=\"1.0\"?>\n" +
                "<methodCall>" +
                "  <methodName>config</methodName>" +
                "  <params>" +
                "    <param><value>" +
                "        <struct>" +
                "          <member><name>group</name><value>com.athaydes</value></member>" +
                "          <member><name>module</name><value>testing</value></member>" +
                "          <member><name>name</name><value>testing-name</value></member>" +
                "          <member><name>version</name><value>2.1</value></member>" +
                "          <member><name>description</name><value>Awesome</value></member>" +
                "          <member><name>url</name><value>http</value></member>" +
                "        </struct>" +
                "    </value></param>" +
                "  </params>" +
                "</methodCall>");

        var doc = response.toDocument();

        assertXml(doc, List.of("methodResponse", "params", "param", "value", "string"))
                .isEqualTo(new JbConfig("com.athaydes", "testing", "testing-name", "2.1", "Awesome",
                        "http", "", "", List.of("src"), "", "",
                        List.of("resources"), List.of(), Map.of(), Map.of(), List.of(), List.of(),
                        "build/compile-libs", "build/runtime-libs", "build/test-reports",
                        List.of(), List.of(), List.of(),
                        Map.of(), Map.of(), Map.of(),
                        null,
                        List.of(), List.of(), Map.of()).toString());
    }

}
