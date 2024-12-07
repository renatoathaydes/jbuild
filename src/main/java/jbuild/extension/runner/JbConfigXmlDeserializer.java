package jbuild.extension.runner;

import jbuild.api.config.DependencyScope;
import jbuild.api.config.DependencySpec;
import jbuild.api.config.Developer;
import jbuild.api.config.JbConfig;
import jbuild.api.config.SourceControlManagement;
import jbuild.util.NoOp;
import org.w3c.dom.Element;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static jbuild.extension.runner.RpcMethodCall.arrayValue;
import static jbuild.extension.runner.RpcMethodCall.extractValue;
import static jbuild.util.XmlUtils.childNamed;
import static jbuild.util.XmlUtils.childrenNamed;
import static jbuild.util.XmlUtils.descendantOf;
import static jbuild.util.XmlUtils.structMember;
import static jbuild.util.XmlUtils.structMemberValue;
import static jbuild.util.XmlUtils.textOf;

final class JbConfigXmlDeserializer {

    static JbConfig from(List<Element> members) {
        return new JbConfig(
                str("group", members, ""),
                str("module", members, ""),
                str("name", members, ""),
                str("version", members, "0.0"),
                str("description", members, ""),
                str("url", members, ""),
                str("main-class", members, ""),
                str("extension-project", members, ""),
                strList("source-dirs", members, List.of("src")),
                str("output-dir", members, ""),
                str("output-jar", members, ""),
                strList("resource-dirs", members, List.of("resources")),
                strList("repositories", members, List.of()),
                deps("dependencies", members),
                deps("processor-dependencies", members),
                strList("dependency-exclusion-patterns", members, List.of()),
                strList("processor-dependency-exclusion-patterns", members, List.of()),
                str("compile-libs-dir", members, "build/compile-libs"),
                str("runtime-libs-dir", members, "build/runtime-libs"),
                str("test-reports-dir", members, "build/test-reports"),
                strList("javac-args", members, List.of()),
                strList("run-java-args", members, List.of()),
                strList("test-java-args", members, List.of()),
                // TODO remove these Map fields
                Map.of(),
                Map.of(),
                Map.of(),
                sourceControlManagementFrom(members),
                developersFrom(members),
                strList("licenses", members, List.of()),
                // TODO support untyped properties
                Map.of());
    }

    private static String str(String name, List<Element> members, String defaultValue) {
        var member = structMemberValue(name, members);
        if (member.isEmpty()) {
            return defaultValue;
        }
        return textOf(member);

    }

    private static List<String> strList(String name, List<Element> members, List<String> defaultValue) {
        var member = structMemberValue(name, members);
        if (member.isEmpty()) {
            return defaultValue;
        }
        var values = arrayValue(childNamed("array", member.get()).orElse(null));
        if (!(values instanceof String[])) {
            throw new IllegalArgumentException("member '" + name +
                    "' should have array value, but gut: " + member);
        }
        return List.of((String[]) values);
    }

    private static Map<String, DependencySpec> deps(String name, List<Element> members) {
        var member = structMember(name, members);
        if (member.isEmpty()) {
            return Map.of();
        }
        var data = descendantOf(member.get(), "value", "array", "data")
                .orElseThrow(() -> new IllegalArgumentException("in '" + name +
                        "': expected value/array/data, not " + member));
        var values = childrenNamed("value", data);
        var structs = values.stream()
                .map(e -> childNamed("struct", e).orElseThrow(() ->
                        new IllegalArgumentException("expected only structs in array of '" + name + "', not " + e)));

        return structs.map(struct -> {
            var structMembers = childrenNamed("member", struct);
            var key = textOf(structMember("key", structMembers));
            var value = structMember("value", structMembers);
            if (key.isEmpty() || value.isEmpty()) {
                throw new IllegalArgumentException("dependency entry in '" + name + "' missing key or value: " + structMembers);
            }
            var depStruct = childNamed("struct", value.get());
            return depStruct.map(s -> new AbstractMap.SimpleEntry<>(key, dependency(key, s)))
                    .orElseGet(() -> new AbstractMap.SimpleEntry<>(key, DependencySpec.DEFAULT));
        }).collect(HashMap::new, (map, e) -> map.put(e.getKey(), e.getValue()), NoOp.ignoreBoth());
    }

    private static DependencySpec dependency(String name, Element struct) {
        try {
            var members = childrenNamed("member", struct);
            var scopeString = textOf(structMember("scope", members).flatMap(s -> childNamed("value", s)));
            var path = textOf(structMember("path", members).flatMap(s -> childNamed("value", s)));
            var transitive = structMember("transitive", members)
                    .flatMap(t -> childNamed("value", t))
                    .map(t -> extractValue(t, boolean.class))
                    .orElse(true);
            return new DependencySpec(transitive, DependencyScope.valueOf(scopeString), path);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid spec for dependency '" + name + "'", e);
        }
    }

    private static SourceControlManagement sourceControlManagementFrom(List<Element> members) {
        var member = structMember("scm", members);
        if (member.isEmpty()) {
            return null;
        }
        var scmStruct = descendantOf(member.get(), "value", "struct");
        if (scmStruct.isEmpty()) {
            throw new IllegalArgumentException("Invalid scm, expected struct, not " + member.get());
        }
        var scmMembers = childrenNamed("member", scmStruct.get());
        var connection = textOf(structMemberValue("connection", scmMembers));
        var devConnection = textOf(structMemberValue("developer-connection", scmMembers));
        var url = textOf(structMemberValue("url", scmMembers));
        return new SourceControlManagement(connection, devConnection, url);
    }

    private static List<Developer> developersFrom(List<Element> members) {
        var member = structMember("developers", members);
        if (member.isEmpty()) {
            return List.of();
        }
        var data = descendantOf(member.get(), "value", "array", "data")
                .orElseThrow(() -> new IllegalArgumentException("in 'developers': " +
                        "expected value/array/data, not " + member));
        var values = childrenNamed("value", data);
        var structs = values.stream()
                .map(e -> childNamed("struct", e).orElseThrow(() ->
                        new IllegalArgumentException("expected only structs in array of 'developers', not " + e)));
        return structs.map(struct -> {
            var structMembers = childrenNamed("member", struct);
            var name = textOf(structMemberValue("name", structMembers));
            var email = textOf(structMemberValue("email", structMembers));
            var org = textOf(structMemberValue("organization", structMembers));
            var orgUrl = textOf(structMemberValue("organization-url", structMembers));
            return new Developer(name, email, org, orgUrl);
        }).collect(Collectors.toList());
    }

}

