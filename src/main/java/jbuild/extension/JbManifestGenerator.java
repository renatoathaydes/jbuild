package jbuild.extension;

import jbuild.api.JBuildException;
import jbuild.api.JBuildException.ErrorCause;
import jbuild.classes.JBuildClassFileParser;
import jbuild.classes.model.ClassFile;
import jbuild.classes.model.attributes.AnnotationInfo;
import jbuild.classes.model.attributes.ElementValuePair;
import jbuild.commands.IncrementalChanges;
import jbuild.extension.ConfigObject.ConfigObjectConstructor;
import jbuild.log.JBuildLog;
import jbuild.util.FileCollection;
import jbuild.util.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static jbuild.util.FileUtils.CLASS_FILES_FILTER;
import static jbuild.util.JavaTypeUtils.typeNameToClassName;

/**
 * jb extension manifest generator.
 */
public final class JbManifestGenerator {

    public static final String JB_TASK_INFO = "Ljbuild/api/JbTaskInfo;";

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*");
    public static final String JB_EXTENSION_FILE = "META-INF/jb/jb-extension.yaml";

    private final JBuildLog log;

    public JbManifestGenerator(JBuildLog log) {
        this.log = log;
    }

    public FileCollection generateJbManifest(String classesDir,
                                             String jarFile,
                                             IncrementalChanges changes) {
        var startTime = System.currentTimeMillis();

        var extensions = new ArrayList<>(findExtensionsClassesDir(classesDir));

        // the extensions in the jar file may be replaced by the ones just compiled (hence, in the class files),
        // so only add those which are not yet in the 'extensions' list.
        extensions.addAll(findExtensionsInJarFile(jarFile, extensions.stream()
                .map(ClassFile::getTypeName)
                .collect(Collectors.toSet())));

        log.verbosePrintln(() -> "Found " + extensions.size() + " jb extension(s) in " +
                (System.currentTimeMillis() - startTime) + " ms");

        if (changes != null) {
            var deletedFiles = changes.deletedFiles.stream()
                    .map(file -> Paths.get(file).getFileName().toString())
                    .collect(Collectors.toSet());

            // find the intersection of source names and deleted files
            var deletedExtensions = extensions.stream()
                    .filter(e -> deletedFiles.contains(e.getSourceFile()))
                    .collect(Collectors.toList());

            log.verbosePrintln(() -> "jb extension source files: [" +
                    extensions.stream()
                            .map(ClassFile::getSourceFile)
                            .collect(Collectors.joining(",")) +
                    "], deleted: [" +
                    deletedExtensions.stream()
                            .map(ClassFile::getSourceFile)
                            .collect(Collectors.joining(",")) + ']');

            if (!deletedExtensions.isEmpty()) {
                log.verbosePrintln(() -> "Removing " + deletedExtensions.size() +
                        " deleted jb extensions from the manifest");
                extensions.removeAll(deletedExtensions);
            }
        }

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
        var classNameByTaskName = new TreeMap<String, String>();
        for (var extension : extensions) {
            createEntryForExtension(extension, yamlBuilder, classNameByTaskName);
        }
        log.verbosePrintln(() -> "Creating jb manifest with the following task(s): " +
                new TreeSet<>(classNameByTaskName.keySet()));
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
        var manifest = dir.resolve(JB_EXTENSION_FILE);
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
        createEntryForExtension(extension, yamlBuilder, new HashMap<>(1));
    }

    private void createEntryForExtension(ClassFile extension, StringBuilder yamlBuilder, Map<String, String> classNameByTaskName) {
        var className = typeNameToClassName(extension.getTypeName());
        log.verbosePrintln(() -> "Creating jb manifest for " + className);
        try {
            for (var annotation : extension.getRuntimeInvisibleAnnotations()) {
                if (annotation.typeDescriptor.equals(JB_TASK_INFO)) {
                    log.verbosePrintln(() -> "Parsing @JbTaskInfo from " + extension.getConstructors());
                    var configObject = ConfigObject.describeConfigObject(extension);
                    writeInfo(className, configObject, annotation.getMap(), yamlBuilder, classNameByTaskName);
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
                           StringBuilder yamlBuilder,
                           Map<String, String> classNameByTaskName) {
        String taskName = safeTaskName(annotation.get("name").value);
        var previousClassName = classNameByTaskName.put(taskName, className);
        if (previousClassName != null) {
            throw new JBuildException("Invalid jb extension: task '" + taskName + "' duplicated in '" +
                    className + "' and '" + previousClassName + "'.",
                    ErrorCause.USER_INPUT);
        }

        yamlBuilder.append("  \"").append(taskName).append("\":\n");
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

    private List<ClassFile> findExtensionsClassesDir(String directory) {
        var parser = new JBuildClassFileParser();
        return FileUtils.collectFiles(directory, CLASS_FILES_FILTER).files.stream()
                .map(classFile -> parseClassFile(parser, classFile))
                .filter(this::implementsJbTask)
                .collect(Collectors.toList());
    }

    private List<ClassFile> findExtensionsInJarFile(String jarFile, Set<String> existingExtensions) {
        if (jarFile == null || !new File(jarFile).exists()) {
            return List.of();
        }
        log.verbosePrintln(() -> "Trying to find jb extensions in existing jar file: " + jarFile);
        var parser = new JBuildClassFileParser();
        try (var zip = new ZipFile(jarFile)) {
            return zip.stream()
                    .filter(entry -> CLASS_FILES_FILTER.accept(null, entry.getName()))
                    .map(classFile -> parseClassFile(parser, jarFile, zip, classFile))
                    .filter(e -> !existingExtensions.contains(e.getTypeName()))
                    .filter(this::implementsJbTask)
                    .collect(Collectors.toList());
        } catch (IOException e) {

            throw new JBuildException("Unable to open jar file at " + jarFile + ": " + e,
                    ErrorCause.IO_READ);
        }
    }

    private boolean implementsJbTask(ClassFile type) {
        return type.getInterfaceNames().contains("Ljbuild/api/JbTask;")
                || warnIfAnnotatedWithTaskInfo(type);
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
                    ErrorCause.IO_READ);
        }
    }

    private static ClassFile parseClassFile(JBuildClassFileParser parser, String jarFile, ZipFile zip, ZipEntry classFile) {
        try {
            return parser.parse(zip.getInputStream(classFile));
        } catch (IOException e) {
            throw new JBuildException("Unable to read class file at " + jarFile + "/" +
                    classFile.getName() + ": " + e,
                    ErrorCause.IO_READ);
        }
    }
}
