package jbuild.extension;

import jbuild.api.JBuildException;
import jbuild.api.JBuildException.ErrorCause;
import jbuild.classes.parser.JBuildClassFileParser;
import jbuild.classes.model.ClassFile;
import jbuild.classes.model.attributes.AnnotationInfo;
import jbuild.extension.ConfigObject.ConfigObjectConstructor;
import jbuild.log.JBuildLog;
import jbuild.util.FileCollection;
import jbuild.util.FileUtils;
import jbuild.util.TextUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.nio.charset.StandardCharsets.UTF_8;
import static jbuild.util.FileUtils.CLASS_FILES_FILTER;
import static jbuild.util.JavaTypeUtils.fileToTypeName;
import static jbuild.util.JavaTypeUtils.typeNameToClassName;

/**
 * jb extension manifest generator.
 */
public final class JbManifestGenerator {

    public static final String JB_TASK_INFO = "Ljbuild/api/JbTaskInfo;";

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*");
    public static final String YAML_TASKS = "tasks:";
    private static final String YAML_CLASS_NAME_PREFIX = "    class-name: \"";
    public static final String YAML_TASK_NAME_PREFIX = "  \"";
    public static final String YAML_PHASE_PREFIX = "    phase:";

    public static final String JB_EXTENSION_FILE = "META-INF/jb/jb-extension.yaml";

    private final JBuildLog log;

    public JbManifestGenerator(JBuildLog log) {
        this.log = log;
    }

    public FileCollection generateJbManifest(String classesDir,
                                             String jarFile,
                                             Set<String> deletions) {
        var startTime = log.isVerbose() ? System.currentTimeMillis() : 0L;

        Map<String, JbManifestEntry> extensionsByTaskName = findExtensionsClassesDir(classesDir)
                .stream()
                .collect(Collectors.toMap(JbManifestEntry::getTaskName, e -> e, (e1, e2) -> {
                    throw new JBuildException("Duplicate jb extension task name: " + e1.getTaskName() +
                            " in " + e1.getClassName() + " and " + e2.getClassName(), ErrorCause.USER_INPUT);
                }, TreeMap::new));

        if (log.isVerbose()) {
            log.verbosePrintln("Found " + extensionsByTaskName.keySet() + " jb task(s) in class files in " +
                    (System.currentTimeMillis() - startTime) + " ms");
        }

        if (jarFile != null) {
            startTime = log.isVerbose() ? System.currentTimeMillis() : 0L;

            // the extensions in the jar file may be replaced by the ones just compiled (hence, in the class files).
            var extensionsInJar = parseJbExtensionsInJar(jarFile);

            if (log.isVerbose()) {
                log.verbosePrintln("Found " + extensionsInJar.stream()
                        .map(JbManifestEntry.Str::getTaskName)
                        .collect(Collectors.toList()) + " jb extension(s) in jar file in " +
                        (System.currentTimeMillis() - startTime) + " ms");
            }

            // the extensions in the jar file may be replaced by the ones just compiled.
            extensionsInJar.forEach(e -> extensionsByTaskName.putIfAbsent(e.getTaskName(), e));
        }

        if (!deletions.isEmpty()) {
            log.verbosePrintln("Trying to find deleted extensions in the deleted files: " + deletions);
            var deletedClasses = deletions.stream()
                    .map(f -> typeNameToClassName(fileToTypeName(f)))
                    .collect(Collectors.toSet());

            // find the intersection of source names and deleted files
            List<JbManifestEntry> deletedExtensions = extensionsByTaskName.values().stream()
                    .filter(e -> deletedClasses.contains(e.getClassName()))
                    .collect(Collectors.toList());

            log.verbosePrintln(() -> "jb extensions: " + extensionsByTaskName.values() +
                    ", deleted: " + deletedExtensions);

            if (!deletedExtensions.isEmpty()) {
                deletedExtensions.forEach(e -> extensionsByTaskName.remove(e.getTaskName()));
            }
        }

        if (extensionsByTaskName.isEmpty()) {
            throw new JBuildException("Cannot generate jb manifest. " +
                    "Failed to locate implementations of jbuild.api.JbTask", ErrorCause.USER_INPUT);
        }

        return createManifest(extensionsByTaskName);
    }

    private FileCollection createManifest(Map<String, ? extends JbManifestEntry> extensionsByTaskName) {
        var startTime = System.currentTimeMillis();
        var yamlBuilder = new StringBuilder(4096);
        yamlBuilder.append(YAML_TASKS).append("\n");
        for (var extension : extensionsByTaskName.values()) {
            extension.with(parsed -> writeInfo(parsed, yamlBuilder), str -> yamlBuilder.append(str.yamlString));
        }
        log.verbosePrintln(() -> "Creating jb manifest with the following task(s): " +
                extensionsByTaskName.keySet());
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
            Files.writeString(manifest, manifestContents, UTF_8);
        } catch (IOException e) {
            throw new JBuildException("Failed to write jb manifest file: " + e, ErrorCause.IO_WRITE);
        }
        return new FileCollection(dir.toString(), List.of(manifest.toString()));
    }

    // VisibleForTesting
    void createEntryForExtension(ClassFile extension, StringBuilder yamlBuilder) {
        var entry = createEntryForExtension(extension);
        writeInfo(entry, yamlBuilder);
    }

    private JbManifestEntry.Parsed createEntryForExtension(ClassFile extension) {
        var className = typeNameToClassName(extension.getTypeName());
        log.verbosePrintln(() -> "Creating jb manifest entry for " + className);
        try {
            for (var annotation : extension.getRuntimeInvisibleAnnotations()) {
                if (annotation.typeDescriptor.equals(JB_TASK_INFO)) {
                    log.verbosePrintln(() -> "Parsing @JbTaskInfo from " + extension.getConstructors());
                    var configObject = ConfigObject.describeConfigObject(extension);
                    var map = annotation.getMap();
                    var taskName = map.get("name");
                    var description = map.get("description");
                    var phase = map.get("phase");
                    String phaseName = null;
                    int phaseIndex = -1;
                    if (phase != null) {
                        var phaseAnnotation = ((AnnotationInfo) phase.value).getMap();
                        phaseName = phaseAnnotation.get("name").value.toString();
                        var phaseIndexValue = phaseAnnotation.get("index");
                        phaseIndex = phaseIndexValue == null ? -1 : (int) phaseIndexValue.value;
                    }
                    return new JbManifestEntry.Parsed(
                            className,
                            taskName.value.toString(),
                            description == null ? null : description.value.toString(),
                            phaseName,
                            phaseIndex,
                            configObject); // only one annotation is allowed
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

    private void writeInfo(JbManifestEntry.Parsed entry,
                           StringBuilder yamlBuilder) {
        yamlBuilder.append(YAML_TASK_NAME_PREFIX).append(safeTaskName(entry.taskName)).append("\":\n");
        yamlBuilder.append(YAML_CLASS_NAME_PREFIX)
                .append(safeYamlString(entry.className, "class", false))
                .append("\"\n");
        var description = entry.description;
        if (description != null) {
            yamlBuilder.append("    description: \"")
                    .append(safeTaskDescription(description))
                    .append('"')
                    .append('\n');
        }
        var phase = entry.phaseName;
        if (phase != null) {
            yamlBuilder.append(YAML_PHASE_PREFIX)
                    .append("\n      \"").append(safePhaseName(phase)).append("\": ")
                    .append(entry.phaseIndex).append('\n');
        }
        writeConstructors(entry.descriptor.constructors, yamlBuilder);
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

    private List<JbManifestEntry.Parsed> findExtensionsClassesDir(String directory) {
        var parser = new JBuildClassFileParser();
        return FileUtils.collectFiles(directory, CLASS_FILES_FILTER).files.stream()
                .map(classFile -> parseClassFile(parser, classFile))
                .filter(this::implementsJbTask)
                .map(this::createEntryForExtension)
                .collect(Collectors.toList());

    }

    private Set<JbManifestEntry.Str> parseJbExtensionsInJar(String jarFile) {
        if (jarFile == null || !new File(jarFile).exists()) {
            return Set.of();
        }
        log.verbosePrintln(() -> "Trying to find jb extensions in existing jar file: " + jarFile);

        try (var zip = new ZipFile(jarFile)) {
            var jbExtension = zip.getEntry(JB_EXTENSION_FILE);
            if (jbExtension == null) {
                log.verbosePrintln(() -> "No jb manifest found in jar: " + jarFile);
                return Set.of();
            }
            log.verbosePrintln(() -> "Found jb manifest in jar: " + jarFile);
            try (var stream = new Scanner(zip.getInputStream(jbExtension), UTF_8)) {
                return parseJbExtensions(stream, jarFile);
            }
        } catch (IOException e) {
            throw new JBuildException("Unable to open jar file at " + jarFile + ": " + e,
                    ErrorCause.IO_READ);
        }
    }

    static HashSet<JbManifestEntry.Str> parseJbExtensions(Scanner stream, String jarFile) {
        var result = new HashSet<JbManifestEntry.Str>(4);
        var currentClass = "";
        var currentTaskName = "";
        var yaml = new StringBuilder(256);
        var lineNumber = 1;

        // ensure the file starts with the expected first line
        if (!stream.nextLine().equals(YAML_TASKS)) {
            throw invalidJbManifest(lineNumber, jarFile, "should start with tasks entry");
        }
        while (stream.hasNextLine()) {
            lineNumber++;
            var line = stream.nextLine();
            if (line.startsWith(YAML_TASK_NAME_PREFIX)) {
                var quoteIndex = line.indexOf('"');
                if (line.endsWith("\":")) {
                    if (!currentClass.isEmpty()) {
                        result.add(new JbManifestEntry.Str(currentClass, currentTaskName, yaml.toString()));
                        // clear the yaml builder for the next task
                        yaml.setLength(0);
                    }
                    currentTaskName = TextUtils.unquote(line.substring(quoteIndex, line.length() - 1));
                    currentClass = "";
                } else {
                    throw invalidJbManifest(lineNumber, jarFile, "expected task name, got " + line);
                }
            } else if (currentClass.isEmpty()) {
                if (line.startsWith(YAML_CLASS_NAME_PREFIX)) {
                    currentClass = TextUtils.unquote(line.substring(YAML_CLASS_NAME_PREFIX.length() - 1));
                } else {
                    throw invalidJbManifest(lineNumber, jarFile, "expected class name, got " + line);
                }
            }
            yaml.append(line).append('\n');
        }
        if (!currentClass.isEmpty()) {
            result.add(new JbManifestEntry.Str(currentClass, currentTaskName, yaml.toString()));
        }
        return result;
    }

    private static JBuildException invalidJbManifest(int lineNumber, String jarFile, String message) {
        return new JBuildException("Invalid jb manifest in jar: " + jarFile + " - " + message, ErrorCause.USER_INPUT);
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
