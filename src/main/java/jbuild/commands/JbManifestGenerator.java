package jbuild.commands;

import jbuild.classes.JBuildClassFileParser;
import jbuild.classes.model.ClassFile;
import jbuild.classes.model.attributes.AnnotationInfo;
import jbuild.classes.model.attributes.ElementValuePair;
import jbuild.errors.JBuildException;
import jbuild.errors.JBuildException.ErrorCause;
import jbuild.log.JBuildLog;
import jbuild.util.FileCollection;
import jbuild.util.FileUtils;
import jbuild.util.JavaTypeUtils;

import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

final class JbManifestGenerator {

    private static final FilenameFilter CLASS_FILES_FILTER = (dir, name) -> name.endsWith(".class");

    private final JBuildLog log;

    JbManifestGenerator(JBuildLog log) {
        this.log = log;
    }

    FileCollection generateJbManifest(String classesDir) {
        var startTime = System.currentTimeMillis();
        var types = findTypeDefinitions(classesDir);
        var extensions = findExtensions(types);
        log.verbosePrintln(() -> "Found " + extensions.size() + " jb extension(s) in " +
                (System.currentTimeMillis() - startTime) + " ms");
        return createManifest(extensions);
    }

    private FileCollection createManifest(List<ClassFile> extensions) {
        var yamlBuilder = new StringBuilder(4096);
        yamlBuilder.append("tasks:\n");
        for (var extension : extensions) {
            createEntryForExtension(extension, yamlBuilder);
        }
        return writeManifest(yamlBuilder);
    }

    private FileCollection writeManifest(CharSequence manifestContents) {
        Path dir;
        try {
            dir = Files.createTempDirectory("jbuild-jb-manifest");
            var manifest = dir.resolve("META-INF/jb/jb-extension.yaml");
            manifest.getParent().toFile().mkdirs();
            Files.writeString(manifest, manifestContents, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new JBuildException("Failed to write jb manifest file: " + e, ErrorCause.IO_WRITE);
        }
        return new FileCollection(dir.toString());
    }

    private void createEntryForExtension(ClassFile extension, StringBuilder yamlBuilder) {
        var className = JavaTypeUtils.typeNameToClassName("L" + extension.getClassName() + ';');
        log.verbosePrintln(() -> "Creating jb manifest for " + className);
        for (var annotation : extension.getRuntimeInvisibleAnnotations()) {
            if (annotation.typeDescriptor.equals("Ljbuild/api/JbTaskInfo;")) {
                writeInfo(className, annotation.getMap(), yamlBuilder);
                return; // only one annotation is allowed
            }
        }
        throw new JBuildException(
                "jb extension '" + className + "' is not annotated with @jbuild.api.JbTaskInfo",
                ErrorCause.USER_INPUT);
    }

    private void writeInfo(String className, Map<String, ElementValuePair> annotation, StringBuilder yamlBuilder) {
        yamlBuilder.append("  \"").append(annotation.get("name").value).append("\":\n");
        yamlBuilder.append("    class-name: ").append(className).append('\n');
        var description = annotation.get("description");
        if (description != null) {
            yamlBuilder.append("    description: ").append(description.value).append('\n');
        }
        var phase = annotation.get("phase");
        if (phase != null) {
            var phaseAnnotation = ((AnnotationInfo) phase.value).getMap();
            yamlBuilder.append("    phase:\n      \"").append(phaseAnnotation.get("name").value)
                    .append("\": ").append(phaseAnnotation.get("index").value).append('\n');
        }
        writeStrings(annotation, "inputs", "inputs", yamlBuilder);
        writeStrings(annotation, "outputs", "outputs", yamlBuilder);
        writeStrings(annotation, "dependsOn", "depends-on", yamlBuilder);
        writeStrings(annotation, "dependents", "dependents", yamlBuilder);
    }

    private static void writeStrings(Map<String, ElementValuePair> annotation,
                                     String section,
                                     String yamlName,
                                     StringBuilder yamlBuilder) {
        var sectionValue = annotation.get(section);
        if (sectionValue != null) {
            var values = (List<?>) sectionValue.value;
            yamlBuilder.append("    ").append(yamlName).append(":\n");
            for (var value : values) {
                yamlBuilder.append("      - \"").append(value.toString().replaceAll("/", "//")).append("\"\n");
            }
        }
    }

    private List<ClassFile> findExtensions(List<ClassFile> types) {
        var extensions = new ArrayList<ClassFile>();
        for (var type : types) {
            if (type.getInterfaceNames().contains("jb.api.JbTask")) {
                extensions.add(type);
            }
        }
        return extensions;
    }

    private static List<ClassFile> findTypeDefinitions(String directory) {
        var parser = new JBuildClassFileParser();
        var classFiles = FileUtils.collectFiles(directory, CLASS_FILES_FILTER);
        return classFiles.files.stream()
                .map(classFile -> parseClassFile(parser, directory))
                .collect(toList());
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
