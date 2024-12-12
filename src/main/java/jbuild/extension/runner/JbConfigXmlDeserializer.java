package jbuild.extension.runner;

import jbuild.api.config.DependencyScope;
import jbuild.api.config.DependencySpec;
import jbuild.api.config.Developer;
import jbuild.api.config.JbConfig;
import jbuild.api.config.SourceControlManagement;
import jbuild.util.NoOp;
import org.w3c.dom.Element;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static jbuild.extension.runner.RpcMethodCall.extractValue;
import static jbuild.extension.runner.RpcMethodCall.untypedMap;
import static jbuild.util.XmlUtils.childNamed;
import static jbuild.util.XmlUtils.childrenNamed;
import static jbuild.util.XmlUtils.descendantOf;
import static jbuild.util.XmlUtils.structMember;
import static jbuild.util.XmlUtils.structMemberValue;

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
                properties(members));
    }

    private static String str(String name, List<Element> members, String defaultValue) {
        var member = structMemberValue(name, members);
        if (member.isEmpty()) {
            return defaultValue;
        }
        return extractValue(member.get(), String.class);
    }

    private static List<String> strList(String name, List<Element> members, List<String> defaultValue) {
        var member = structMemberValue(name, members);
        if (member.isEmpty()) {
            return defaultValue;
        }
        var values = childNamed("array", member.get()).map(RpcMethodCall::arrayValue).orElse(new String[0]);
        if (values instanceof Object[] && ((Object[]) values).length == 0) {
            return List.of();
        }
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
        var depsStruct = descendantOf(member.get(), "value", "struct")
                .orElseThrow(() -> new IllegalArgumentException("in '" + name +
                        "': expected value/struct, not " + member));
        var depMembers = childrenNamed("member", depsStruct);

        return depMembers.stream().map(depMember -> {
            var depKey = childNamed("name", depMember);
            var depValue = descendantOf(depMember, "value", "struct");
            if (depKey.isEmpty()) {
                throw new IllegalArgumentException("in '" + name +
                        "': dependency struct missing name: " + depMember);
            }
            try {
                var dep = extractValue(depKey.get(), String.class);
                return new AbstractMap.SimpleEntry<>(dep, depValue
                        .map(d -> dependency(dep, d))
                        .orElse(DependencySpec.DEFAULT));
            } catch (Exception e) {
                throw new IllegalArgumentException("in '" + name +
                        "': dependency '" + depKey.get(), e);
            }
        }).collect(LinkedHashMap::new, (map, e) -> map.put(e.getKey(), e.getValue()), NoOp.ignoreBoth());
    }

    private static DependencySpec dependency(String name, Element struct) {
        try {
            var members = childrenNamed("member", struct);
            var scopeString = str("scope", members, "all");
            var path = str("path", members, "");
            var transitive = structMemberValue("transitive", members)
                    .map(t -> extractValue(t, boolean.class))
                    .orElse(true);
            return new DependencySpec(transitive, DependencyScope.fromString(scopeString), path);
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
        var connection = str("connection", scmMembers, "");
        var devConnection = str("developer-connection", scmMembers, "");
        var url = str("url", scmMembers, "");
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
            try {
                var structMembers = childrenNamed("member", struct);
                var name = str("name", structMembers, "");
                var email = str("email", structMembers, "");
                var org = str("organization", structMembers, "");
                var orgUrl = str("organization-url", structMembers, "");
                return new Developer(name, email, org, orgUrl);
            } catch (Exception e) {
                throw new IllegalArgumentException("in 'dependencies'.", e);
            }
        }).collect(Collectors.toList());
    }

    private static Map<String, Object> properties(List<Element> members) {
        var member = structMember("properties", members);
        if (member.isEmpty()) {
            return Map.of();
        }

        var propertiesStruct = descendantOf(member.get(), "value", "struct")
                .orElseThrow(() ->
                        new IllegalArgumentException("expected struct value under 'properties', not " + member));

        return untypedMap(childrenNamed("member", propertiesStruct));
    }

}
