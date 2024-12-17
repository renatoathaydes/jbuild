package jbuild.extension;

import jbuild.api.JBuildException;
import jbuild.api.JBuildException.ErrorCause;
import jbuild.classes.JBuildClassFileParser;
import jbuild.classes.model.ClassFile;
import jbuild.classes.model.attributes.AnnotationInfo;
import jbuild.classes.model.attributes.ElementValuePair;
import jbuild.extension.ConfigObject.ConfigObjectConstructor;
import jbuild.log.JBuildLog;
import jbuild.util.FileCollection;
import jbuild.util.FileUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.toList;
import static jbuild.util.JavaTypeUtils.typeNameToClassName;

/**
 * jb extension manifest generator.
 */
public final class JbManifestGenerator {

    public static final String JB_TASK_INFO = "Ljbuild/api/JbTaskInfo;";

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*");

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
        var className = typeNameToClassName(extension.getTypeName());
        log.verbosePrintln(() -> "Creating jb manifest for " + className);
        try {
            for (var annotation : extension.getRuntimeInvisibleAnnotations()) {
                if (annotation.typeDescriptor.equals(JB_TASK_INFO)) {
                    log.verbosePrintln(() -> "Parsing @JbTaskInfo from " + extension.getConstructors());
                    var configObject = ConfigObject.describeConfigObject(extension);
                    writeInfo(className, configObject, annotation.getMap(), yamlBuilder);
                    return; // only one annotation is allowed
                }
            }
        } catch (JBuildException e) {
            throw new JBuildException("jb extension '" + className + "' could not be created: " +
                    e.getMessage(), e.getErrorCause());
        } catch (Exception e) {
            throw new RuntimeException("jb extension '" + className + "' could not be created, " +
                    "failed to extract metadata from class file", e);
        }
        throw new JBuildException(
                "jb extension '" + className + "' is not annotated with @jbuild.api.JbTaskInfo",
                ErrorCause.USER_INPUT);
    }

    private void writeInfo(String className,
                           ConfigObject.ConfigObjectDescriptor configObject,
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
            var phaseIndexValue = phaseAnnotation.get("index");
            var phaseIndex = phaseIndexValue == null ? -1 : phaseIndexValue.value;
            yamlBuilder.append("    phase:\n      \"")
                    .append(safePhaseName(phaseAnnotation.get("name").value))
                    .append("\": ").append(phaseIndex).append('\n');
        }
        writeConstructors(configObject.constructors, yamlBuilder);
    }

    private void writeConstructors(List<ConfigObjectConstructor> constructors, StringBuilder yamlBuilder) {
        yamlBuilder.append("    config-constructors:\n");
        for (var constructor : constructors) {
            if (constructor.parameters.isEmpty()) {
                yamlBuilder.append("      - {}\n");
            } else {
                var isFirst = true;
                for (var entry : constructor.parameters.entrySet()) {
                    var name = entry.getKey();
                    var configType = entry.getValue();
                    yamlBuilder.append(isFirst ? "      - " : "        ")
                            .append("\"").append(name).append("\": ");
                    yamlBuilder.append('"').append(configType.name()).append("\"\n");
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
        var string = value.toString();
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
                        typeNameToClassName(type.getTypeName()) +
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

}
