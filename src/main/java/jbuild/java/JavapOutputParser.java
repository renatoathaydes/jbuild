package jbuild.java;

import jbuild.java.code.ClassDefinition;
import jbuild.java.code.Code;
import jbuild.java.code.MethodDefinition;
import jbuild.log.JBuildLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class JavapOutputParser {

    private static final Pattern CODE_LINE = Pattern.compile("\\s*\\d+:.*\\s//\\s([A-Za-z].+)");

    private final JBuildLog log;

    public JavapOutputParser(JBuildLog log) {
        this.log = log;
    }

    public ClassDefinition processJavapOutput(String className, Iterator<String> lines) {
        String prevLine = null, name = "", type = "";
        var methods = new HashMap<MethodDefinition, List<Code>>();
        var fields = new HashMap<String, Code.Field>();
        var expectingCode = false;
        while (lines.hasNext()) {
            var line = lines.next();
            if (prevLine == null) {
                prevLine = line;
                continue;
            }
            if (expectingCode) {
                expectingCode = false;
                if (line.equals("    Code:")) {
                    var method = new MethodDefinition(name, type);
                    var code = processCode(lines);
                    methods.put(method, code);
                }
            } else if (line.startsWith("    descriptor: ")) {
                if (prevLine.contains("(")) { // method
                    name = extractMethodName(prevLine); // descriptor always appears after the definition's name
                    if (name == null) continue;
                    type = line.substring("    descriptor: ".length());
                    expectingCode = true; // after the descriptor, comes the code
                } else { // field
                    name = extractFieldName(prevLine);
                    if (name == null) continue;
                    type = line.substring("    descriptor: ".length());
                    fields.put(name, new Code.Field(name, type));
                }
            }
            prevLine = line;
        }
        return new ClassDefinition(className, fields, methods);
    }

    public List<Code> processCode(Iterator<String> lines) {
        var result = new ArrayList<Code>();
        while (lines.hasNext()) {
            var line = lines.next();
            if (line.isEmpty()) { // this ends the code section
                break;
            }
            var match = CODE_LINE.matcher(line);
            if (match.matches()) {
                var parts = match.group(1).split("\\s");
                handleCommentParts(parts).ifPresent(result::add);
            }
        }
        return result;
    }

    private Optional<? extends Code> handleCommentParts(String[] parts) {
        if (parts.length == 2) {
            switch (parts[0]) {
                case "String":
                    break;
                case "Method":
                case "InterfaceMethod":
                    return parseMethod(parts[1]);
                case "class":
                    return parseClass(parts[1]);
                case "Field":
                    return parseField(parts[1]);
            }
        }
        return Optional.empty();
    }

    private Optional<Code.ClassRef> parseClass(String classDef) {
        return Optional.of(new Code.ClassRef(classDef));
    }

    private Optional<Code.Method> parseMethod(String method) {
        var parts1 = method.split("\\.", 2);
        if (parts1.length != 2) return Optional.empty();
        var parts2 = parts1[1].split(":");
        if (parts2.length != 2) return Optional.empty();
        return Optional.of(new Code.Method(parts1[0], parts2[0], parts2[1]));
    }

    private Optional<Code.Field> parseField(String field) {
        var parts = field.split(":", 2);
        if (parts.length != 2) return Optional.empty();
        return Optional.of(new Code.Field(parts[0], parts[1]));
    }

    private String extractMethodName(String line) {
        var argsStart = line.indexOf('(');
        var nameStart = line.lastIndexOf(' ', argsStart - 1);
        if (nameStart < 0 || argsStart < 0) {
            log.println(() -> "WARNING: unable to find method name on line '" + line + "'");
            return null;
        }
        return line.substring(nameStart + 1, argsStart);
    }

    private String extractFieldName(String line) {
        var lastSpaceIndex = line.lastIndexOf(' ');
        if (lastSpaceIndex < 0 || !line.endsWith(";")) {
            log.println(() -> "WARNING: unable to find field name on line '" + line + "'");
            return null;
        }
        return line.substring(lastSpaceIndex + 1, line.length() - 1);
    }

}
