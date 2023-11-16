package jbuild.extension;

import jbuild.api.JBuildException;
import jbuild.api.JBuildException.ErrorCause;
import jbuild.classes.JBuildClassFileParser;
import jbuild.classes.model.ClassFile;
import jbuild.classes.model.attributes.AnnotationInfo;
import jbuild.classes.model.attributes.ElementValuePair;
import jbuild.classes.model.attributes.MethodParameter;
import jbuild.classes.signature.JavaTypeSignature;
import jbuild.classes.signature.JavaTypeSignature.ReferenceTypeSignature.ArrayTypeSignature;
import jbuild.classes.signature.JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature;
import jbuild.classes.signature.MethodSignature;
import jbuild.classes.signature.SimpleClassTypeSignature;
import jbuild.classes.signature.SimpleClassTypeSignature.TypeArgument;
import jbuild.log.JBuildLog;
import jbuild.util.FileCollection;
import jbuild.util.FileUtils;
import jbuild.util.JavaTypeUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.toList;

/**
 * jb extension manifest generator.
 */
public final class JbManifestGenerator {

    public static final String JB_TASK_INFO = "Ljbuild/api/JbTaskInfo;";

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*");

    private static final ClassTypeSignature STRING =
            new ClassTypeSignature("java.lang", new SimpleClassTypeSignature("String"));

    private static final ClassTypeSignature LIST_STRING =
            new ClassTypeSignature("java.util", new SimpleClassTypeSignature("List", List.of(
                    new TypeArgument.Reference(STRING))));

    private static final ArrayTypeSignature ARRAY_STRING = new ArrayTypeSignature((short) 1, STRING);

    private final JBuildLog log;

    public JbManifestGenerator(JBuildLog log) {
        this.log = log;
    }

    public FileCollection generateJbManifest(String classesDir) {
        var startTime = System.currentTimeMillis();
        var extensions = findExtensions(classesDir);
        log.verbosePrintln(() -> "Found " + extensions.size() + " jb extension(s) in " +
                (System.currentTimeMillis() - startTime) + " ms");
        if (extensions.isEmpty()) {
            throw new JBuildException("Cannot generate jb manifest. " +
                    "Failed to locate implementations of jbuild.api.JbTask", ErrorCause.USER_INPUT);
        }
        return createManifest(extensions);
    }

    private FileCollection createManifest(List<ClassFile> extensions) {
        var startTime = System.currentTimeMillis();
        var yamlBuilder = new StringBuilder(4096);
        yamlBuilder.append("tasks:\n");
        for (var extension : extensions) {
            createEntryForExtension(extension, yamlBuilder);
        }
        var manifestFiles = writeManifest(yamlBuilder);
        log.verbosePrintln(() -> "Created jb manifest in " + (System.currentTimeMillis() - startTime) + " ms");
        return manifestFiles;
    }

    private FileCollection writeManifest(CharSequence manifestContents) {
        Path dir;
        try {
            dir = Files.createTempDirectory("jbuild-jb-manifest");
        } catch (IOException e) {
            throw new JBuildException("Unable to create temp directory: " + e, ErrorCause.IO_WRITE);
        }
        var manifest = dir.resolve("META-INF/jb/jb-extension.yaml");
        try {
            if (!manifest.getParent().toFile().mkdirs()) {
                throw new JBuildException("Failed to create temp directory for jb manifest file", ErrorCause.IO_WRITE);
            }
            Files.writeString(manifest, manifestContents, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new JBuildException("Failed to write jb manifest file: " + e, ErrorCause.IO_WRITE);
        }
        return new FileCollection(dir.toString(), List.of(manifest.toString()));
    }

    // VisibleForTesting
    void createEntryForExtension(ClassFile extension, StringBuilder yamlBuilder) {
        var className = JavaTypeUtils.typeNameToClassName(extension.getTypeName());
        log.verbosePrintln(() -> "Creating jb manifest for " + className);
        try {
            for (var annotation : extension.getRuntimeInvisibleAnnotations()) {
                if (annotation.typeDescriptor.equals(JB_TASK_INFO)) {
                    var configObject = describeConfigObject(extension);
                    writeInfo(className, configObject, annotation.getMap(), yamlBuilder);
                    return; // only one annotation is allowed
                }
            }
        } catch (JBuildException e) {
            throw new JBuildException("jb extension '" + className + "' could not be created: " +
                    e.getMessage(), e.getErrorCause());
        }
        throw new JBuildException(
                "jb extension '" + className + "' is not annotated with @jbuild.api.JbTaskInfo",
                ErrorCause.USER_INPUT);
    }

    private ConfigObjectDescriptor describeConfigObject(ClassFile extension) {
        var className = JavaTypeUtils.typeNameToClassName(extension.getTypeName());
        var constructors = extension.getConstructors().stream().map(constructor -> {
            var params = extension.getMethodParameters(constructor);
            ensureParameterNamesAvailable(params, className);

            // prefer to use the generic type if it's available as we need the type parameters
            return extension.getSignatureAttribute(constructor)
                    .map(signature -> createConstructor(className, params, signature))
                    .orElseGet(() -> createConstructor(className, params,
                            extension.getMethodTypeDescriptor(constructor)));
        });
        return new ConfigObjectDescriptor(constructors.collect(toList()));
    }

    private static void ensureParameterNamesAvailable(List<MethodParameter> params, String className) {
        if (params.stream().anyMatch(it -> it.name.isEmpty())) {
            throw new JBuildException("Constructor of class " + className + " has unnamed parameters - " +
                    "make sure to compile the class with the javac -parameters option " +
                    "or use a Java record for the configuration object.",
                    ErrorCause.USER_INPUT);
        }
    }

    private static ConfigObjectConstructor createConstructor(
            String className, List<MethodParameter> params, MethodSignature genericType) {
        var paramTypes = new LinkedHashMap<String, ConfigType>();
        if (!genericType.typeParameters.isEmpty()) {
            throw new JBuildException("Constructor of class " + className +
                    " is generic, which is not allowed in a jb extension.",
                    ErrorCause.USER_INPUT);
        }
        var paramIndex = 0;
        for (var arg : genericType.arguments) {
            var param = params.get(paramIndex++);
            var type = toConfigType(arg, className, param.name);
            paramTypes.put(param.name, type);
        }
        return new ConfigObjectConstructor(paramTypes);
    }

    private void writeInfo(String className,
                           ConfigObjectDescriptor configObject,
                           Map<String, ElementValuePair> annotation,
                           StringBuilder yamlBuilder) {
        yamlBuilder.append("  \"").append(safeTaskName(annotation.get("name").value)).append("\":\n");
        yamlBuilder.append("    class-name: ").append(className).append('\n');
        var description = annotation.get("description");
        if (description != null) {
            yamlBuilder.append("    description: ").append(safeTaskDescription(description.value)).append('\n');
        }
        var phase = annotation.get("phase");
        if (phase != null) {
            var phaseAnnotation = ((AnnotationInfo) phase.value).getMap();
            yamlBuilder.append("    phase:\n      \"")
                    .append(safePhaseName(phaseAnnotation.get("name").value))
                    .append("\": ").append(phaseAnnotation.get("index").value).append('\n');
        }
        writeStrings(annotation, "inputs", "inputs", yamlBuilder, false);
        writeStrings(annotation, "outputs", "outputs", yamlBuilder, false);
        writeStrings(annotation, "dependsOn", "depends-on", yamlBuilder, true);
        writeStrings(annotation, "dependents", "dependents", yamlBuilder, true);
        writeConstructors(configObject.constructors, yamlBuilder);
    }

    private static void writeStrings(Map<String, ElementValuePair> annotation,
                                     String section,
                                     String yamlName,
                                     StringBuilder yamlBuilder,
                                     boolean validateTaskName) {
        var sectionValue = annotation.get(section);
        if (sectionValue != null) {
            var values = (List<?>) sectionValue.value;
            yamlBuilder.append("    ").append(yamlName).append(":\n");
            for (var value : values) {
                var allowWhitespace = !validateTaskName;
                yamlBuilder.append("      - \"").append(safeYamlString(value, section, allowWhitespace)).append("\"\n");
            }
        }
    }

    private void writeConstructors(List<ConfigObjectConstructor> constructors, StringBuilder yamlBuilder) {
        yamlBuilder.append("    config-constructors:\n");
        for (var constructor : constructors) {
            if (constructor.parameters.isEmpty()) {
                yamlBuilder.append("      - {}\n");
            } else {
                var isFirst = true;
                for (var entry : constructor.parameters.entrySet()) {
                    yamlBuilder.append(isFirst ? "      - " : "        ");
                    yamlBuilder.append("\"").append(entry.getKey()).append("\": \"")
                            .append(entry.getValue().name()).append("\"\n");
                    isFirst = false;
                }
            }
        }
    }

    private static String safeTaskName(Object value) {
        return safeYamlString(value, "task name", false);
    }

    private static String safeTaskDescription(Object value) {
        return safeYamlString(value, "task description", true);
    }

    private static String safePhaseName(Object value) {
        var string = (String) value;
        if (!SAFE_IDENTIFIER.matcher(string).matches()) {
            throw new JBuildException(
                    "value of 'phase name' is not a valid identifier",
                    ErrorCause.USER_INPUT);
        }
        return string;
    }

    private static String safeYamlString(Object value, String property, boolean allowWhitespace) {
        var string = (String) value;
        string.chars().forEach((ch) -> {
            if (ch == '"') throwInvalidCharacter('"', property);
            if (ch == '\n') throwInvalidCharacter('\n', property);
            if (!allowWhitespace && Character.isWhitespace(ch)) throwInvalidCharacter(' ', property);
        });
        return string;
    }

    private static void throwInvalidCharacter(char c, String property) {
        throw new JBuildException(
                "value of '" + property + "' contains invalid character: '" + c + "'",
                ErrorCause.USER_INPUT);
    }

    private List<ClassFile> findExtensions(String directory) {
        var parser = new JBuildClassFileParser();
        return FileUtils.collectFiles(directory, FileUtils.CLASS_FILES_FILTER).files.stream()
                .map(classFile -> parseClassFile(parser, classFile))
                .filter(type -> type.getInterfaceNames().contains("Ljbuild/api/JbTask;")
                        || warnIfAnnotatedWithTaskInfo(type))
                .collect(toList());
    }

    private boolean warnIfAnnotatedWithTaskInfo(ClassFile type) {
        for (var annotation : type.getRuntimeInvisibleAnnotations()) {
            if (annotation.typeDescriptor.equals(JB_TASK_INFO)) {
                log.println(() -> "WARNING: class " +
                        JavaTypeUtils.typeNameToClassName(type.getTypeName()) +
                        "is annotated with @JbTaskInfo but does not implement JbTask, " +
                        "so it will not become a jb task!");
            }
        }

        return false;
    }

    private static ClassFile parseClassFile(
            JBuildClassFileParser parser,
            String classFile) {
        try (var stream = new FileInputStream(classFile)) {
            return parser.parse(stream);
        } catch (IOException e) {
            throw new JBuildException("Unable to read class file at " + classFile + ": " + e,
                    ErrorCause.ACTION_ERROR);
        }
    }

    private static ConfigType toConfigType(JavaTypeSignature arg, String className, String name) {
        if (arg instanceof JavaTypeSignature.BaseType) {
            var argType = (JavaTypeSignature.BaseType) arg;
            if (argType == JavaTypeSignature.BaseType.Z) return ConfigType.BOOLEAN;
            if (argType == JavaTypeSignature.BaseType.I) return ConfigType.INT;
            if (argType == JavaTypeSignature.BaseType.F) return ConfigType.FLOAT;
        } else if (arg instanceof ClassTypeSignature) {
            var refType = (ClassTypeSignature) arg;
            if (STRING.equals(refType)) return ConfigType.STRING;
            if (LIST_STRING.equals(refType)) return ConfigType.LIST_OF_STRINGS;
        } else if (arg instanceof ArrayTypeSignature) {
            var arrayType = (ArrayTypeSignature) arg;
            if (ARRAY_STRING.equals(arrayType)) return ConfigType.ARRAY_OF_STRINGS;
        }
        throw new JBuildException("At class " + className + ", constructor parameter '" + name +
                "' has an unsupported type for jb extension (use String, String[], List<String> or a primitive type)",
                ErrorCause.USER_INPUT);
    }

    private enum ConfigType {
        STRING, BOOLEAN, INT, FLOAT, LIST_OF_STRINGS, ARRAY_OF_STRINGS,
    }

    private static final class ConfigObjectConstructor {
        final Map<String, ConfigType> parameters;

        public ConfigObjectConstructor(LinkedHashMap<String, ConfigType> parameters) {
            this.parameters = parameters;
        }
    }

    private static final class ConfigObjectDescriptor {

        final List<ConfigObjectConstructor> constructors;

        public ConfigObjectDescriptor(List<ConfigObjectConstructor> constructors) {
            this.constructors = constructors;
        }
    }
}
