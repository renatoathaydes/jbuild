package jbuild.commands;

import static java.util.stream.Collectors.toList;
import static jbuild.java.tools.Tools.verifyToolSuccessful;

import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import jbuild.errors.JBuildException;
import jbuild.errors.JBuildException.ErrorCause;
import jbuild.java.JavapOutputParser;
import jbuild.java.code.AnnotationValues;
import jbuild.java.code.TypeDefinition;
import jbuild.java.tools.Tools.Javap;
import jbuild.log.JBuildLog;
import jbuild.util.FileCollection;
import jbuild.util.FileUtils;
import jbuild.util.JavaTypeUtils;

final class JbManifestGenerator {

    private static final FilenameFilter CLASS_FILES_FILTER = (dir, name) -> name.endsWith(".class");

    private final JBuildLog log;

    JbManifestGenerator(JBuildLog log) {
        this.log = log;
    }

    FileCollection generateJbManifest(String classesDir) {
        var types = findTypeDefinitions(classesDir);
        var extensions = findExtensions(types);
        return createManifest(extensions);
    }

    private FileCollection createManifest(List<TypeDefinition> extensions) {
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

    private void createEntryForExtension(TypeDefinition extension, StringBuilder yamlBuilder) {
        var className = JavaTypeUtils.typeNameToClassName(extension.typeName);
        for (var annotation : extension.annotationValues) {
            if (annotation.name.equals("jbuild.api.JbTaskInfo")) {
                writeInfo(className, annotation, yamlBuilder);
                break; // only one annotation is allowed
            }
        }
    }

    private void writeInfo(String className, AnnotationValues annotation, StringBuilder yamlBuilder) {
        yamlBuilder.append("  \"").append(annotation.getString("name")).append("\":\n");
        yamlBuilder.append("    class-name: ").append(className).append('\n');
        var description = annotation.getString("description");
        if (!description.isBlank()) {
            yamlBuilder.append("    description: ").append(description).append('\n');
        }
        var phase = annotation.getSub("phase");
        if (phase.getInt("index") != 400) {
            yamlBuilder.append("    phase:\n      \"").append(phase.getString("name"))
                    .append("\": ").append(phase.getInt("index")).append('\n');
        }
        writeStrings(annotation, "inputs", "inputs", yamlBuilder);
        writeStrings(annotation, "outputs", "outputs", yamlBuilder);
        writeStrings(annotation, "dependsOn", "depends-on", yamlBuilder);
        writeStrings(annotation, "dependents", "dependents", yamlBuilder);
    }

    private static void writeStrings(AnnotationValues annotation,
            String section,
            String yamlName,
            StringBuilder yamlBuilder) {
        var values = annotation.getAllStrings(section);
        if (!values.isEmpty()) {
            yamlBuilder.append("    ").append(yamlName).append(":\n");
            for (var value : values) {
                yamlBuilder.append("      - \"").append(value.replaceAll("/", "//")).append("\"\n");
            }
        }
    }

    private List<TypeDefinition> findExtensions(Collection<Map<String, TypeDefinition>> types) {
        var extensions = new ArrayList<TypeDefinition>();
        for (var typeMap : types) {
            for (var typeDef : typeMap.values()) {
                if (typeDef.implementedInterfaces.contains("jb.api.JbTask")) {
                    extensions.add(typeDef);
                }
            }
        }
        return extensions;
    }

    private Collection<Map<String, TypeDefinition>> findTypeDefinitions(String directory) {
        var javap = Javap.create();
        var javapOutputParser = new JavapOutputParser(log);
        var classFiles = FileUtils.collectFiles(directory, CLASS_FILES_FILTER);
        return classFiles.files.stream()
                .map(classFile -> parseClassFile(javap, javapOutputParser, directory))
                .collect(toList());
    }

    private Map<String, TypeDefinition> parseClassFile(
            Javap javap,
            JavapOutputParser javapOutputParser,
            String classFile) {
        var toolResult = javap.run(classFile);
        verifyToolSuccessful("javap", toolResult);

        try (var stdoutStream = toolResult.getStdoutLines();
                var ignored = toolResult.getStderrLines()) {
            return javapOutputParser.processJavapOutput(stdoutStream.iterator());
        } catch (JBuildException e) {
            throw new JBuildException(e.getMessage() + " (class: " + classFile + ")", e.getErrorCause());
        }

    }
}
