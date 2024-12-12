package jbuild.extension.runner;

import jbuild.api.change.ChangeKind;
import jbuild.api.change.ChangeSet;
import jbuild.api.change.FileChange;
import jbuild.api.config.DependencyScope;
import jbuild.api.config.DependencySpec;
import jbuild.api.config.Developer;
import jbuild.api.config.JbConfig;
import jbuild.api.config.SourceControlManagement;
import jbuild.java.TestHelper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
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
    void canRunMethodTakingStructArg_JbConfig_minimal() throws Exception {
        var caller = new RpcCaller(TestCallable.class.getName());
        var response = caller.call("<?xml version=\"1.0\"?>\n" +
                "<methodCall>" +
                "  <methodName>config</methodName>" +
                "  <params>" +
                "    <param><value>" +
                "        <struct>" +
                "          <member><name>module</name><value>testing</value></member>" +
                "        </struct>" +
                "    </value></param>" +
                "  </params>" +
                "</methodCall>");

        var doc = response.toDocument();

        assertXml(doc, List.of("methodResponse", "params", "param", "value", "string"))
                .isEqualTo(new JbConfig("", "testing", "", "0.0", "",
                        "", "", "", List.of("src"), "", "",
                        List.of("resources"), List.of(), Map.of(), Map.of(), List.of(), List.of(),
                        "build/compile-libs", "build/runtime-libs", "build/test-reports",
                        List.of(), List.of(), List.of(),
                        Map.of(), Map.of(), Map.of(),
                        null,
                        List.of(), List.of(), Map.of()).toString());
    }

    @Test
    void canRunMethodTakingStructArg_JbConfig_emptyArrayAndCustomProperties() throws Exception {
        var caller = new RpcCaller(TestCallable.class.getName());
        var response = caller.call("<?xml version=\"1.0\"?>\n" +
                "<methodCall>" +
                "  <methodName>config</methodName>" +
                "  <params>" +
                "    <param><value>" +
                "        <struct>" +
                "          <member><name>module</name><value>testing</value></member>" +
                "          <member><name>resource-dirs</name><value><array><data></data></array></value></member>" +
                "          <member><name>properties</name><value>\n" +
                "            <struct>\n" +
                "              <member>\n" +
                "                  <name>versions</name>\n" +
                "                  <value>\n" +
                "                      <struct>\n" +
                "                          <member>\n" +
                "                              <name>java</name>\n" +
                "                              <value>\n" +
                "                                  <int>11</int>\n" +
                "                              </value>\n" +
                "                          </member>\n" +
                "                          <member>\n" +
                "                              <name>junit</name>\n" +
                "                              <value>5.9.1</value>\n" +
                "                          </member>\n" +
                "                      </struct>\n" +
                "                  </value>\n" +
                "              </member>\n" +
                "          </struct></value></member>" +
                "        </struct>" +
                "    </value></param>" +
                "  </params>" +
                "</methodCall>");

        var doc = response.toDocument();

        assertXml(doc, List.of("methodResponse", "params", "param", "value", "string"))
                .isEqualTo(new JbConfig("", "testing", "", "0.0", "",
                        "", "", "", List.of("src"), "", "",
                        List.of(), List.of(), Map.of(), Map.of(), List.of(), List.of(),
                        "build/compile-libs", "build/runtime-libs", "build/test-reports",
                        List.of(), List.of(), List.of(),
                        Map.of(), Map.of(), Map.of(),
                        null,
                        List.of(), List.of(),
                        Map.of("versions", new LinkedHashMap<>() {{
                            put("java", 11);
                            put("junit", "5.9.1");
                        }})).toString());
    }

    @Test
    void canRunMethodTakingStructArg_JbConfig_all() throws Exception {
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
                "          <member><name>main-class</name><value>my.Main</value></member>" +
                "          <member><name>extension-project</name><value>ext-project</value></member>" +
                "          <member><name>source-dirs</name><value><array><data>" +
                "            <value>src/main/java</value>" +
                "            <value>src/gen/java</value>" +
                "          </data></array></value></member>" +
                "          <member><name>output-dir</name><value>out-dir</value></member>" +
                "          <member><name>output-jar</name><value>out.jar</value></member>" +
                "          <member><name>resource-dirs</name><value><array><data>" +
                "            <value>src/resources</value>" +
                "          </data></array></value></member>" +
                "          <member><name>repositories</name><value><array><data>" +
                "            <value>http://my.repo</value>" +
                "          </data></array></value></member>" +
                "          <member><name>dependencies</name><value>" +
                "            <struct>" +
                "              <member><name>org.dep:dep:1.0</name>" +
                "              <value><struct>" +
                "                   <member><name>transitive</name><value><boolean>1</boolean></value></member>" +
                "                   <member><name>scope</name><value>compile-only</value></member>" +
                "                   <member><name>path</name><value>p</value></member>" +
                "                </struct></value></member></struct></value></member>" +
                "          <member><name>processor-dependencies</name><value>" +
                "            <struct>" +
                "              <member><name>a.b:c:2</name>" +
                "              <value><struct>" +
                "                   <member><name>transitive</name><value><boolean>0</boolean></value></member>" +
                "                   <member><name>scope</name><value>runtime-only</value></member>" +
                "                   <member><name>path</name><value></value></member>" +
                "                </struct></value></member>" +
                "              <member><name>a.b:d:3</name>" +
                "              <value><struct>" +
                "                   <member><name>transitive</name><value><boolean>1</boolean></value></member>" +
                "                   <member><name>scope</name><value>all</value></member>" +
                "                   <member><name>path</name><value></value></member>" +
                "                </struct></value></member>" +
                "            </struct></value></member>" +
                "          <member><name>dependency-exclusion-patterns</name><value>" +
                "               <array><data><value>exclude/*</value></data></array></value></member>" +
                "          <member><name>processor-dependency-exclusion-patterns</name><value>" +
                "               <array><data><value>proc-exclude/*</value></data></array></value></member>" +
                "          <member><name>compile-libs-dir</name><value>build/compile-libs</value></member>" +
                "          <member><name>runtime-libs-dir</name><value>build/runtime-libs</value></member>" +
                "          <member><name>test-reports-dir</name><value>build/test-reports</value></member>" +
                "          <member><name>javac-args</name><value><array><data>" +
                "               <value>--release</value>" +
                "               <value>11</value>" +
                "             </data></array></value></member>" +
                "          <member><name>run-java-args</name><value><array><data>" +
                "               <value>--foo</value>" +
                "             </data></array></value></member>" +
                "          <member><name>test-java-args</name><value><array><data>" +
                "               <value>--test</value>" +
                "             </data></array></value></member>" +
                "          <member><name>scm</name><value>" +
                "             <struct>" +
                "               <member><name>connection</name><value>con</value></member>" +
                "               <member><name>developer-connection</name><value>dev</value></member>" +
                "               <member><name>url</name><value>scm-url</value></member>" +
                "             </struct>" +
                "           </value></member>" +
                "          <member><name>developers</name><value><array><data>" +
                "             <value><struct>" +
                "               <member><name>name</name><value>joe</value></member>" +
                "               <member><name>email</name><value>joe@com</value></member>" +
                "               <member><name>organization</name><value>ACME</value></member>" +
                "               <member><name>organization-url</name><value>acme.com</value></member>" +
                "             </struct></value>" +
                "             </data></array></value></member>" +
                "          <member><name>licenses</name><value><array><data>" +
                "            <value>APACHE-2.0</value>" +
                "            <value>MIT</value>" +
                "            </data></array></value></member>" +
                "        </struct>" +
                "    </value></param>" +
                "  </params>" +
                "</methodCall>");

        var doc = response.toDocument();

        assertXml(doc, List.of("methodResponse", "params", "param", "value", "string"))
                .isEqualTo(new JbConfig("com.athaydes", "testing", "testing-name", "2.1", "Awesome",
                        "http", "my.Main", "ext-project", List.of("src/main/java", "src/gen/java"),
                        "out-dir", "out.jar",
                        List.of("src/resources"), List.of("http://my.repo"),
                        Map.of("org.dep:dep:1.0", new DependencySpec(true, DependencyScope.COMPILE_ONLY, "p")),
                        // use LinkedHashMap to ensure toString prints the same thing
                        new LinkedHashMap<>() {{
                            put("a.b:c:2", new DependencySpec(false, DependencyScope.RUNTIME_ONLY, ""));
                            put("a.b:d:3", new DependencySpec(true, DependencyScope.ALL, ""));
                        }},
                        List.of("exclude/*"), List.of("proc-exclude/*"),
                        "build/compile-libs", "build/runtime-libs", "build/test-reports",
                        List.of("--release", "11"), List.of("--foo"), List.of("--test"),
                        Map.of(), Map.of(), Map.of(),
                        new SourceControlManagement("con", "dev", "scm-url"),
                        List.of(new Developer("joe", "joe@com", "ACME", "acme.com")),
                        List.of("APACHE-2.0", "MIT"),
                        Map.of()).toString());
    }

}
