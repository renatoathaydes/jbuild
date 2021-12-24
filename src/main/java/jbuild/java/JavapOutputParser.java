package jbuild.java;

import jbuild.java.code.ClassDefinition;
import jbuild.java.code.Code;
import jbuild.java.code.FieldDefinition;
import jbuild.java.code.MethodDefinition;
import jbuild.log.JBuildLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class JavapOutputParser {

    private static final Pattern CLASS_NAME_LINE = Pattern.compile("((public|private|protected|final)\\s)*" +
            "(class|interface|enum)\\s([a-zA-Z_$.0-9]+)(\\s.*)?");

    private static final Pattern CODE_LINE = Pattern.compile("\\s*\\d+:.*\\s//\\s([A-Za-z].+)");

    private static final Pattern METHOD_HANDLE_LINE = Pattern.compile("\\s*#\\d+\\s=\\sMethodHandle\\s+.*//\\s+(.*)");

    private final JBuildLog log;

    public JavapOutputParser(JBuildLog log) {
        this.log = log;
    }

    public List<ClassDefinition> processJavapOutput(Iterator<String> lines) {
        var result = new ArrayList<ClassDefinition>();
        var waitingForClassLine = false;
        while (lines.hasNext()) {
            var line = lines.next();
            if (waitingForClassLine) {
                var match = CLASS_NAME_LINE.matcher(line);
                if (match.matches()) {
                    var className = match.group(4);
                    var classDef = processJavapOutput(className, lines);
                    result.add(classDef);
                    waitingForClassLine = false;
                } else if (!line.startsWith(" ")) {
                    log.println("WARNING: did not find class name after Classfile section started. " +
                            "Ignoring class from line '" + line + "'");
                    waitingForClassLine = false;
                }
            } else if (line.startsWith("Classfile jar:")) {
                waitingForClassLine = true;
            }
        }
        return result;
    }

    public ClassDefinition processJavapOutput(String className, Iterator<String> lines) {
        String typeName = classNameToTypeName(className);
        Set<Code.Method> methodHandles = null;
        while (lines.hasNext()) {
            var line = lines.next();
            if (line.equals("Constant pool:")) {
                methodHandles = parseConstantPool(typeName, lines);
                break;
            }
        }
        if (methodHandles == null) throw new IllegalStateException("Constant pool not found for class " + className);
        return processClassMethods(typeName, methodHandles, lines);
    }

    private Set<Code.Method> parseConstantPool(String typeName, Iterator<String> lines) {
        Set<Code.Method> methodHandles = new LinkedHashSet<>(4);
        while (lines.hasNext()) {
            var line = lines.next();
            var match = METHOD_HANDLE_LINE.matcher(line);
            if (match.matches()) {
                var method = extractMethodHandle(typeName, match.group(1));
                if (method != null) methodHandles.add(method);
            }
            if (line.equals("{")) {
                return methodHandles;
            }
        }
        log.println(() -> "WARNING: reached the end of the class file without finishing the constant pool section");
        return methodHandles;
    }

    public ClassDefinition processClassMethods(String typeName,
                                               Set<Code.Method> methodHandles,
                                               Iterator<String> lines) {
        String prevLine = null, name = "", type = "";
        var methods = new HashMap<MethodDefinition, Set<Code>>();
        var fields = new LinkedHashSet<FieldDefinition>();
        boolean expectingCode = false, expectingFlags = false;
        while (lines.hasNext()) {
            var line = lines.next();
            if (prevLine == null) {
                prevLine = line;
                continue;
            }
            if (line.equals("}") // normal end of class
                    || line.startsWith("SourceFile: \"")) { // the code section might end with '}', so the next line will be this
                break;
            }
            if (expectingFlags) {
                expectingFlags = false;
                expectingCode = true;
            } else if (expectingCode) {
                expectingCode = false;
                if (line.equals("    Code:")) {
                    var method = new MethodDefinition(name, type);
                    var code = processCode(lines, typeName);
                    methods.put(method, code);
                }
            } else if (line.startsWith("    descriptor: ")) {
                if (prevLine.equals("  static {};")) { // static block
                    name = "static{}";
                    type = "()V";
                    expectingFlags = true;
                } else if (prevLine.contains("(")) { // method
                    name = extractMethodName(prevLine); // descriptor always appears after the definition's name
                    if (name == null) continue;
                    type = line.substring("    descriptor: ".length());
                    expectingFlags = true; // after the descriptor, comes the flags then code
                } else { // field
                    name = extractFieldName(prevLine);
                    if (name == null) continue;
                    type = line.substring("    descriptor: ".length());
                    fields.add(new FieldDefinition(name, type));
                }
            }
            prevLine = line;
        }
        return new ClassDefinition(typeName, fields, methodHandles, methods);
    }

    public Set<Code> processCode(Iterator<String> lines, String typeName) {
        var result = new LinkedHashSet<Code>();
        while (lines.hasNext()) {
            var line = lines.next();
            if (line.isEmpty() || line.equals("}")) { // this ends the code section
                break;
            }
            var match = CODE_LINE.matcher(line);
            if (match.matches()) {
                var parts = match.group(1).split("\\s");
                handleCommentParts(parts, typeName).ifPresent(result::add);
            }
        }
        return result;
    }

    private Optional<? extends Code> handleCommentParts(String[] parts, String typeName) {
        if (parts.length == 2) {
            switch (parts[0]) {
                case "String":
                    break;
                case "Method":
                case "InterfaceMethod":
                    return parseMethod(parts[1], typeName);
                case "class":
                    return parseClass(parts[1], typeName);
                case "Field":
                    return parseField(parts[1], typeName);
            }
        }
        return Optional.empty();
    }

    private Optional<Code.ClassRef> parseClass(String classDef,
                                               String typeName) {
        var type = classNameToTypeName(classDef);
        if (shouldIgnoreClass(type, typeName)) return Optional.empty();
        return Optional.of(new Code.ClassRef(type));
    }

    private Optional<Code.Method> parseMethod(String method,
                                              String typeName) {
        var parts1 = method.split("\\.", 2);
        if (parts1.length != 2) return Optional.empty(); // unexpected, maybe WARNING?
        var parts2 = parts1[1].split(":");
        if (parts2.length != 2) return Optional.empty(); // unexpected, maybe WARNING?
        var type = classNameToTypeName(parts1[0]);
        if (shouldIgnoreClass(type, typeName)) return Optional.empty();
        return Optional.of(new Code.Method(type, parts2[0], parts2[1]));
    }

    private Optional<Code.Field> parseField(String field,
                                            String typeName) {
        var parts = field.split(":", 2);
        if (parts.length != 2) return Optional.empty();
        var nameParts = parts[0].split("\\.", 2);
        var type = classNameToTypeName(nameParts[0]);
        if (nameParts.length != 2 // own field, we don't care about it
                || shouldIgnoreClass(type, typeName)) {
            return Optional.empty();
        }
        return Optional.of(new Code.Field(type, nameParts[1], parts[1]));
    }

    private String extractMethodName(String line) {
        var argsStart = line.indexOf('(');
        var nameStart = line.lastIndexOf(' ', argsStart - 1);
        if (nameStart < 0 || argsStart < 0) {
            log.println(() -> "WARNING: unable to find method name on line '" + line + "'");
            return null;
        }
        // methods are actually often constructors, so we need to convert their names to type names
        var name = line.substring(nameStart + 1, argsStart);
        return name.contains(".") ? classNameToTypeName(name) : name;
    }

    private Code.Method extractMethodHandle(String typeName, String line) {
        int classStart, nameStart, typeStart;
        classStart = line.lastIndexOf(' ');
        if (classStart > 0) {
            classStart++;
            nameStart = line.indexOf(".", classStart);
            if (nameStart > 0) {
                nameStart++;
                typeStart = line.indexOf(':', nameStart);
                if (typeStart > 0) {
                    typeStart++;
                    var type = classNameToTypeName(line.substring(classStart, nameStart - 1));
                    if (shouldIgnoreClass(type, typeName)) return null;
                    return new Code.Method(
                            type,
                            line.substring(nameStart, typeStart - 1),
                            line.substring(typeStart));
                }
            }
        }

        log.println(() -> "WARNING: unable to find method handle on line '" + line + "'");
        return null;
    }

    private String extractFieldName(String line) {
        var lastSpaceIndex = line.lastIndexOf(' ');
        if (lastSpaceIndex < 0 || !line.endsWith(";")) {
            log.println(() -> "WARNING: unable to find field name on line '" + line + "'");
            return null;
        }
        return line.substring(lastSpaceIndex + 1, line.length() - 1);
    }

    /**
     * Ignore the given type if it's a Java API or the own class being parsed.
     * <p>
     * That's because we are only interested in collecting cross-library references.
     *
     * @param type      a type reference
     * @param className name of the current class being parsed
     * @return true if this type should be ignored for the purposes of this library, false otherwise
     */
    private static boolean shouldIgnoreClass(String type, String className) {
        type = cleanArrayTypeName(type);
        return type.startsWith("Ljava/") || type.equals(className);
    }

    private static String cleanArrayTypeName(String type) {
        if (type.startsWith("\"[") && type.endsWith(";\"")) {
            var index = type.lastIndexOf('[');
            return type.substring(index + 1, type.length() - 1);
        }
        return type;
    }

    private static String classNameToTypeName(String className) {
        // array type reference, leave it as it is
        if (className.startsWith("\"")
                // avoid converting already converted type names
                || (className.startsWith("L") && className.endsWith(";"))
        ) return className;

        return "L" + className.replaceAll("\\.", "/") + ";";
    }

}