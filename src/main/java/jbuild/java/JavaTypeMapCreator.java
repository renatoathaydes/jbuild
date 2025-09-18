package jbuild.java;

import jbuild.api.JBuildException;
import jbuild.api.JBuildLogger;
import jbuild.classes.ClassFileException;
import jbuild.classes.model.AccessFlags;
import jbuild.classes.model.ClassFile;
import jbuild.classes.parser.JBuildClassFileParser;
import jbuild.util.FileUtils;
import jbuild.util.JavaTypeUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.jar.JarFile;

import static jbuild.api.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.api.JBuildException.ErrorCause.IO_READ;
import static jbuild.api.JBuildException.ErrorCause.USER_INPUT;

public final class JavaTypeMapCreator {

    private final JBuildLogger log;

    private final JBuildClassFileParser parser = new JBuildClassFileParser();

    public JavaTypeMapCreator(JBuildLogger log) {
        this.log = log;
    }

    public Map<String, JavaType> getTypeMapsFrom(File file) {
        return getTypeMapsFrom(file, Set.of());
    }

    public Map<String, JavaType> getTypeMapsFrom(File file,
                                                 Collection<String> classNames) {
        if (!file.isFile()) {
            throw new JBuildException(file + " is not a file", USER_INPUT);
        }
        var result = new HashMap<String, JavaType>();
        if (FileUtils.CLASS_FILES_FILTER.accept(null, file.getName())) {
            log.verbosePrintln(() -> "Reading class file: " + file.getPath());
            try (var stream = Files.newInputStream(file.toPath())) {
                putTypesFrom(stream, file::getPath, classNames, result);
            } catch (IOException e) {
                throw new JBuildException("could not read " + file.getPath(), IO_READ);
            }
            return result;
        }

        // consider the input a jar
        log.verbosePrintln(() -> "Reading file as jar: " + file.getPath());

        try (var jar = new JarFile(file)) {
            putTypesFrom(jar, classNames, result);
        } catch (IOException e) {
            throw new JBuildException("could not read " + file.getPath(), IO_READ);
        }

        return result;
    }

    private void putTypesFrom(JarFile jar,
                              Collection<String> classNames,
                              Map<String, JavaType> result) throws IOException {
        var entries = jar.entries();
        while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            if (FileUtils.CLASS_FILES_FILTER.accept(null, entry.getName())) {
                log.verbosePrintln(() -> "Reading class file from jar: " + entry.getName());
                try (var stream = jar.getInputStream(entry)) {
                    putTypesFrom(stream, () -> jar.getName() + "!" + entry.getName(), classNames, result);
                }
            } else {
                log.verbosePrintln(() -> "Skipping jar entry: " + entry.getName());
            }
        }
    }

    private void putTypesFrom(InputStream stream,
                              Supplier<String> description,
                              Collection<String> classNames,
                              Map<String, JavaType> result) throws IOException {
        ClassFile classFile;
        try {
            classFile = parser.parse(stream);
        } catch (ClassFileException e) {
            throw new JBuildException("failed to parse class file " + description, ACTION_ERROR);
        }
        var className = JavaTypeUtils.typeNameToClassName(classFile.getTypeName());
        if (include(className, classNames)) {
            result.put(className, createJavaType(classFile));
        }
    }

    private static JavaType createJavaType(ClassFile classFile) {
        var typeId = new JavaType.TypeId(classFile.getTypeName(), kindOf(classFile));
        return new JavaType(typeId, classFile);
    }

    private static JavaType.Kind kindOf(ClassFile classFile) {
        if (AccessFlags.isEnum(classFile.accessFlags)) return JavaType.Kind.ENUM;
        if (AccessFlags.isInterface(classFile.accessFlags)) return JavaType.Kind.INTERFACE;
        return JavaType.Kind.CLASS;
    }

    private static boolean include(String className, Collection<String> classNames) {
        if (classNames.isEmpty()) return true;
        return classNames.contains(className);
    }
}
