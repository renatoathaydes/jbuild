package jbuild.java;

import jbuild.api.JBuildException;
import jbuild.java.code.Code;
import jbuild.java.code.Definition;
import jbuild.java.code.TypeDefinition;
import jbuild.log.JBuildLog;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static jbuild.api.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.java.code.Code.Field.Instruction.getfield;
import static jbuild.java.code.Code.Field.Instruction.getstatic;
import static jbuild.java.code.Code.Field.Instruction.putfield;
import static jbuild.java.code.Code.Field.Instruction.putstatic;
import static jbuild.java.code.Code.Method.Instruction.invokeinterface;
import static jbuild.java.code.Code.Method.Instruction.invokespecial;
import static jbuild.java.code.Code.Method.Instruction.invokestatic;
import static jbuild.java.code.Code.Method.Instruction.invokevirtual;
import static jbuild.java.code.Code.Method.Instruction.other;
import static jbuild.util.JavaTypeUtils.classNameToTypeName;
import static jbuild.util.JavaTypeUtils.cleanArrayTypeName;
import static jbuild.util.JavaTypeUtils.mayBeJavaStdLibType;

/**
 * Parser of javap output.
 */
public final class JavapOutputParser {

    // example:
    //       1: invokespecial #1                  // Method java/lang/Object."<init>":()V
    private static final Pattern CODE_LINE = Pattern.compile("\\s*\\d+:\\s*([a-zA-Z_-]+)[a-zA-Z\\d\\s#,:_]+\\s//\\s([A-Za-z].+)");

    private static final Pattern METHOD_HANDLE_LINE = Pattern.compile("\\s*#\\d+\\s=\\sMethodHandle\\s[a-zA-Z\\d_@$#!?:\\-\\s]*//\\s+(.*)");
    private static final Pattern METHOD_SIGNATURE_LINE = Pattern.compile("Signature:\\s[\\d\\s#]+//\\s(.+)");

    private final JBuildLog log;
    private final JavaTypeParser typeParser;
    private final boolean includeJavaAndPrivateCalls;

    public JavapOutputParser(JBuildLog log) {
        this(log, new JavaTypeParser(log.isVerbose()), false);
    }

    public JavapOutputParser(JBuildLog log,
                             JavaTypeParser typeParser,
                             boolean includeJavaAndPrivateCalls) {
        this.log = log;
        this.typeParser = typeParser;
        this.includeJavaAndPrivateCalls = includeJavaAndPrivateCalls;
    }

    /**
     * Process the output lines emitted by javap.
     * <p>
     * javap must be invoked with the following flags: {@code -c -s -v}, and optionally, {@code -p}.
     *
     * @param lines javap output lines
     * @return map of types by their names
     * @see jbuild.java.tools.Tools.Javap
     */
    public Map<String, TypeDefinition> processJavapOutput(Iterator<String> lines) {
        var result = new LinkedHashMap<String, TypeDefinition>();
        var waitingForClassLine = false;
        while (lines.hasNext()) {
            var line = lines.next();
            if (waitingForClassLine) {
                if (line.startsWith(" ")) continue; // wait for first line without indent
                waitingForClassLine = false;
                var isGeneric = line.contains("<");
                if (isGeneric) {
                    var typeId = typeParser.parseTypeId(line);
                    if (typeId != null) {
                        var typeDef = processBody(typeId, lines);
                        if (lines.hasNext()) {
                            var type = parseTypeSignature(lines, typeId, typeDef);
                            if (type == null) continue;
                            result.put(typeId.name, type);
                        } else {
                            log.println("WARNING: did not find generic type signature after Classfile section started. " +
                                    "Ignoring class from line '" + line + "'");
                        }
                    } else {
                        log.println("WARNING: could not parse generic type from class name after Classfile section started. " +
                                "Ignoring class from line '" + line + "'");
                    }
                } else {
                    var type = typeParser.parse(line);
                    if (type != null) {
                        var typeDef = processBody(type.typeId, lines);
                        result.put(type.typeId.name, typeDef.toTypeDefinition(type));
                    } else {
                        log.println("WARNING: could not parse type from class name after Classfile section started. " +
                                "Ignoring class from line '" + line + "'");
                    }
                }
            } else if (line.startsWith("Classfile ")) {
                waitingForClassLine = true;
            }
        }
        return result;
    }

    private JavaTypeDefinitions processBody(JavaType.TypeId typeId,
                                            Iterator<String> lines) {
        Set<Code.Method> methodHandles = null;
        while (lines.hasNext()) {
            var line = lines.next();
            if (line.equals("Constant pool:")) {
                methodHandles = parseConstantPool(typeId.name, lines);
                break;
            }
        }
        if (methodHandles == null)
            throw new IllegalStateException("Constant pool not found for class " + typeId.name);

        return processClassBody(typeId, methodHandles, lines);
    }

    private Set<Code.Method> parseConstantPool(String typeName, Iterator<String> lines) {
        Set<Code.Method> methodHandles = new LinkedHashSet<>(4);
        while (lines.hasNext()) {
            var line = lines.next();
            if (line.equals("{")) {
                return methodHandles;
            }
            var match = METHOD_HANDLE_LINE.matcher(line);
            if (match.matches()) {
                var method = extractMethodHandle(typeName, match.group(1));
                if (method != null) methodHandles.add(method);
            }
        }
        log.println(() -> "WARNING: reached the end of the class file without finishing the constant pool section");
        return methodHandles;
    }

    private JavaTypeDefinitions processClassBody(JavaType.TypeId typeId,
                                                 Set<Code.Method> methodHandles,
                                                 Iterator<String> lines) {
        var typeName = typeId.name;
        String prevLine = null, name = "", type = "";
        var methods = new HashMap<Definition.MethodDefinition, Set<Code>>();
        var fields = new LinkedHashSet<Definition.FieldDefinition>();
        boolean expectingCode = false, isStatic = false;
        while (lines.hasNext()) {
            var line = lines.next();
            if (line.equals("}")) break;
            try {
                if (prevLine == null || line.isEmpty()) continue;
                if (!line.startsWith("  ")) { // indent must be less than the type body's
                    throw new JBuildException("Unexpected line inside class: '" + line + "'. " +
                            "Previous line was '" + prevLine + "'. Parsing type: '" + typeName + "'.",
                            ACTION_ERROR);
                }
                if (expectingCode) {
                    if (line.equals("    Code:")) {
                        expectingCode = false;
                        var method = new Definition.MethodDefinition(methodOrConstructorName(typeName, name), type,
                                isStatic || "static{}".equals(name));
                        var codeSection = processCode(lines, typeName, name);
                        methods.put(method, codeSection.code);
                        if (codeSection.endsTypeSection) break;
                        line = ""; // this line has been used, set prevLine to nothing
                    } else if (!line.startsWith("    ")) { // make sure we don't accidentally enter another section
                        throw new JBuildException("Expected to find code section but got unexpected line: '" +
                                line + "', previous line was: '" + prevLine + "'. Parsing type: '" + typeName + "'.",
                                ACTION_ERROR);
                    }
                } else if (line.startsWith("    descriptor: ")) {
                    if (prevLine.equals("  static {};")) { // static block
                        name = "static{}";
                        type = "()V";
                        expectingCode = true;
                    } else if (prevLine.contains("(")) { // method
                        name = extractMethodName(prevLine, typeName); // descriptor always appears after the definition's name
                        if (name == null) {
                            throw new JBuildException("Expected method line but got '" + prevLine +
                                    "', descriptor line: '" + line.substring("    descriptor: ".length()) +
                                    "'. Parsing type: '" + typeName + "'.",
                                    ACTION_ERROR);
                        }
                        type = line.substring("    descriptor: ".length());
                        isStatic = prevLine.startsWith("static ") || prevLine.contains(" static ");
                        if (prevLine.contains(" abstract ") || prevLine.contains(" native ")) {
                            var method = new Definition.MethodDefinition(methodOrConstructorName(typeName, name), type, isStatic);
                            methods.put(method, Set.of());
                        } else { // collect the code for the method
                            expectingCode = true;
                        }
                    } else { // field
                        name = extractFieldName(prevLine);
                        if (name == null) {
                            throw new JBuildException("Expected field line but got '" + prevLine +
                                    "', descriptor line: '" + line.substring("    descriptor: ".length()) +
                                    "'. Parsing type: '" + typeName + "'.",
                                    ACTION_ERROR);
                        }
                        type = line.substring("    descriptor: ".length());
                        isStatic = prevLine.startsWith("static ") || prevLine.contains(" static ");
                        fields.add(new Definition.FieldDefinition(name, type, isStatic));
                    }
                }
            } finally {
                prevLine = line;
            }
        }

        return new JavaTypeDefinitions(fields, methodHandles, methods);
    }

    private CodeSection processCode(Iterator<String> lines, String typeName, String methodName) {
        var codes = new LinkedHashSet<Code>();
        boolean endTypeSection = false, consumeRestOfBody = false;
        while (lines.hasNext()) {
            var line = lines.next();

            if (!line.startsWith("      ")) { // indentation must be less than the Code section's
                if (line.startsWith("    ")) { // more inner sections inside this method
                    consumeRestOfBody = true;
                    break;
                }
                endTypeSection = line.equals("}");
                if (!endTypeSection && !line.isEmpty()) {
                    throw new JBuildException("code section of '" + typeName +
                            "#" + methodName + "' did not end with '}': '" + line + "'", ACTION_ERROR);
                }
                break;
            }
            var match = CODE_LINE.matcher(line);
            if (match.matches()) {
                var jvmInstruction = match.group(1);
                var parts = match.group(2).split("\\s");
                var code = handleCommentParts(jvmInstruction, parts, typeName, line);
                if (code != null) codes.add(code);
            }
        }
        if (consumeRestOfBody) {
            while (lines.hasNext()) {
                var line = lines.next();
                if (!line.startsWith("    ")) {
                    endTypeSection = line.equals("}");
                    if (!endTypeSection && !line.isEmpty()) {
                        throw new JBuildException("code section of '" + typeName +
                                "#" + methodName + "' did not end with '}': '" + line + "'", ACTION_ERROR);
                    }
                    break;
                }
            }
        }
        return new CodeSection(codes, endTypeSection);
    }

    private TypeDefinition parseTypeSignature(Iterator<String> lines, JavaType.TypeId typeId, JavaTypeDefinitions typeDef) {
        var skippingSection = false;
        while (lines.hasNext()) {
            var line = lines.next();
            if (skippingSection && line.startsWith(" ")) continue;
            var match = METHOD_SIGNATURE_LINE.matcher(line);
            if (!match.matches()) {
                var tooLate = line.startsWith("SourceFile:"); // too late, missed type signature
                if (!tooLate && !line.startsWith(" ")) { // unrecognized section, skip it
                    skippingSection = true;
                    continue;
                }
                throw new JBuildException("invalid javap output: expected method signature but got '" +
                        line + "'. Current type is: " + typeId.name, ACTION_ERROR);
            }
            var type = typeParser.parseSignature(typeId, match.group(1));
            if (type != null) {
                if (type.typeId.kind == JavaType.Kind.ENUM) {
                    typeDef = typeDef.withMethods(addEnumMethods(typeDef.methods));
                }
                return typeDef.toTypeDefinition(type);
            } else {
                log.println("WARNING: could not parse generic type signature, " +
                        "ignoring class from line '" + line + "': bad signature: '" + line);
                return null;
            }
        }

        throw new JBuildException("invalid javap output: ran out of output while expecting " +
                "method signature parsing type: " + typeId.name, ACTION_ERROR);
    }

    // special-case enum methods as it's not currently possible to find parent classes' methods yet
    private Map<Definition.MethodDefinition, Set<Code>> addEnumMethods(Map<Definition.MethodDefinition, Set<Code>> methods) {
        methods.put(new Definition.MethodDefinition("ordinal", "()I"), Set.of());
        methods.put(new Definition.MethodDefinition("name", "()Ljava/lang/String;"), Set.of());
        return methods;
    }

    private Code handleCommentParts(String jvmInstruction, String[] parts, String typeName, String line) {
        if (parts.length == 2) {
            switch (parts[0]) {
                case "String":
                    break;
                case "Method":
                    // jvmInstruction: invokespecial|invokestatic|invokevirtual|invokeinterface
                case "InterfaceMethod":
                    if (shouldParseMethodCall(parts)) return parseMethod(
                            parts[1], typeName,
                            methodInstruction(jvmInstruction), line);
                    break;
                case "class":
                    // jvmInstruction: checkcast|ldc|new
                    return parseClass(parts[1], typeName);
                case "Field":
                    // jvmInstruction: getstatic|putstatic|getfield|putfield
                    return parseField(parts[1], typeName, fieldInstruction(jvmInstruction));
            }
        }
        return null;
    }

    private static Code.Method.Instruction methodInstruction(String jvmInstruction) {
        switch (jvmInstruction) {
            case "invokespecial":
                return invokespecial;
            case "invokestatic":
                return invokestatic;
            case "invokevirtual":
                return invokevirtual;
            case "invokeinterface":
                return invokeinterface;
            default:
                return other;
        }
    }

    private static Code.Field.Instruction fieldInstruction(String jvmInstruction) {
        switch (jvmInstruction) {
            case "getstatic":
                return getstatic;
            case "putstatic":
                return putstatic;
            case "getfield":
                return getfield;
            case "putfield":
                return putfield;
            default:
                return Code.Field.Instruction.other;
        }
    }

    private Code.Type parseClass(String classDef,
                                 String typeName) {
        var type = classNameToTypeName(classDef);
        if (shouldIgnoreClass(type, typeName)) return null;
        return new Code.Type(type);
    }

    private Code.Method parseMethod(String method,
                                    String typeName,
                                    Code.Method.Instruction instruction,
                                    String line) {
        var parts1 = method.split("\\.", 2);
        var receiverIsThisType = parts1.length == 1;
        String type, methodPart;
        if (receiverIsThisType) {
            type = typeName;
            methodPart = parts1[0];
        } else if (parts1.length != 2) {
            log.println(() -> "WARNING: unexpected javap method line, expected a '.' " +
                    "after the name: '" + method + "'" + "\nLine: " + line);
            return null;
        } else {
            type = classNameToTypeName(parts1[0]);
            methodPart = parts1[1];
        }
        var parts2 = methodPart.split(":", -1);
        if (parts2.length != 2) {
            log.println(() -> "WARNING: unexpected javap method line, expected a ':' " +
                    "between name and type signature: '" + method + "'" + "\nLine: " + line);
            return null;
        }

        if (!receiverIsThisType && shouldIgnoreClass(type, typeName)) return null;
        return new Code.Method(type, parts2[0], parts2[1], instruction);
    }

    private Code.Field parseField(String field,
                                  String typeName,
                                  Code.Field.Instruction instruction) {
        var parts = field.split(":", 2);
        if (parts.length != 2) return null;
        var nameParts = parts[0].split("\\.", 2);
        var type = classNameToTypeName(nameParts[0]);
        if (nameParts.length != 2 // own field, we don't care about it
                || shouldIgnoreClass(type, typeName)) {
            return null;
        }
        return new Code.Field(type, nameParts[1], parts[1], instruction);
    }

    private String extractMethodName(String line, String typeName) {
        var argsStart = line.indexOf('(');
        var nameStart = line.lastIndexOf(' ', argsStart - 1);
        if (nameStart < 0 || argsStart < 0) {
            log.println(() -> "WARNING: unable to find method name on line '" + line + "'");
            return null;
        }
        // methods are actually often constructors, so we need to try convert their names to type names
        var name = line.substring(nameStart + 1, argsStart);
        var methodAsTypeName = classNameToTypeName(name);
        if (typeName.equals(methodAsTypeName)) {
            return typeName;
        }
        return name;
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
                    var jvmInstruction = parseMethodHandleJvmInstruction(line);
                    return new Code.Method(
                            type,
                            line.substring(nameStart, typeStart - 1),
                            line.substring(typeStart),
                            jvmInstruction);
                }
            }
        }

        log.println(() -> "WARNING: unable to find method handle on line '" + line + "'");
        return null;
    }

    private Code.Method.Instruction parseMethodHandleJvmInstruction(String line) {
        // Example: REF_invokeVirtual java/lang/Object.toString:()Ljava/lang/String;
        if (line.startsWith("REF_")) {
            var firstSpaceIndex = line.indexOf(' ');
            if (firstSpaceIndex > 0) {
                return methodInstruction(line.substring("REF_".length(), firstSpaceIndex).toLowerCase(Locale.ROOT));
            }
        }
        return other;
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
    private boolean shouldIgnoreClass(String type, String className) {
        if (includeJavaAndPrivateCalls) return false;
        type = cleanArrayTypeName(type);
        return mayBeJavaStdLibType(type) || type.equals(className);
    }

    private boolean shouldParseMethodCall(String[] parts) {
        if (includeJavaAndPrivateCalls) return true;

        // only look at method calls that have a receiver (i.e. not this)
        return parts[1].contains(".");
    }

    private static String methodOrConstructorName(String typeName, String methodName) {
        return typeName.equals(methodName) ? "\"<init>\"" : methodName;
    }

    private static final class JavaTypeDefinitions {

        final Set<Definition.FieldDefinition> fields;
        final Set<Code.Method> methodHandles;
        final Map<Definition.MethodDefinition, Set<Code>> methods;

        JavaTypeDefinitions(Set<Definition.FieldDefinition> fields,
                            Set<Code.Method> methodHandles,
                            Map<Definition.MethodDefinition, Set<Code>> methods) {
            this.fields = fields;
            this.methodHandles = methodHandles;
            this.methods = methods;
        }

        TypeDefinition toTypeDefinition(JavaType type) {
            return new TypeDefinition(type, fields, methodHandles, methods);
        }

        public JavaTypeDefinitions withMethods(Map<Definition.MethodDefinition, Set<Code>> methods) {
            return new JavaTypeDefinitions(fields, methodHandles, methods);
        }
    }

    private static final class CodeSection {
        final Set<Code> code;
        boolean endsTypeSection;

        CodeSection(Set<Code> code, boolean endsTypeSection) {
            this.code = code;
            this.endsTypeSection = endsTypeSection;
        }
    }

}
