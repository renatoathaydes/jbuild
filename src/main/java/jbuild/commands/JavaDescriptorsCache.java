package jbuild.commands;

import jbuild.api.JBuildException;
import jbuild.classes.model.ClassFile;
import jbuild.java.ClassGraph;
import jbuild.util.JavaTypeUtils;

import java.util.Objects;
import java.util.stream.Stream;

import static jbuild.api.JBuildException.ErrorCause.ACTION_ERROR;

final class JavaDescriptorsCache {

    public static Stream<String> findFieldDescriptorsByName(
            ClassFile classFile,
            String targetName,
            ClassGraph classGraph) {
        return expandWithJavaSuperTypes(classFile, classGraph)
                .flatMap(c -> Stream.of(c.getFields()))
                .filter(f -> f.getName().equals(targetName))
                .map(f -> JavaTypeUtils.toTypeDescriptor(f.getType()));
    }

    public static Stream<String> findMethodDescriptorsByName(
            ClassFile classFile,
            String targetName,
            ClassGraph classGraph) {
        return expandWithJavaSuperTypes(classFile, classGraph)
                .flatMap(c -> Stream.of(c.getMethods()))
                .filter(f -> f.getName().equals(targetName))
                .map(m -> JavaTypeUtils.toMethodTypeDescriptor(m.getReturnType(), m.getParameterTypes()));
    }

    static Stream<ClassFile> expandWithSuperTypes(ClassFile classFile, ClassGraph classGraph) {
        var superClasses = Stream.iterate(
                classFile,
                Objects::nonNull,
                cf -> {
                    var superClass = cf.getSuperClass();
                    var superType = classGraph.findTypeDefinition(superClass);
                    return superType == null ? null : superType.classFile;
                }
        );
        return Stream.concat(superClasses,
                findSuperInterfaces(classFile, classGraph));
    }

    private static Stream<ClassFile> findSuperInterfaces(ClassFile classFile, ClassGraph classGraph) {
        return classFile.getInterfaceNames().stream()
                .flatMap(interfaceName -> {
                    var def = classGraph.findTypeDefinition(interfaceName);
                    if (def == null) return Stream.empty();
                    return Stream.concat(Stream.of(def.classFile),
                            findSuperInterfaces(def.classFile, classGraph));
                });
    }

    private static Stream<Class<?>> expandWithJavaSuperTypes(ClassFile classFile, ClassGraph classGraph) {
        Class<?> superClass = findJavaSuperType(classFile, classGraph);
        return Stream.concat(Stream.of(superClass),
                findJavaSuperInterfaces(classFile, classGraph));
    }

    private static Stream<Class<?>> findJavaSuperInterfaces(ClassFile classFile, ClassGraph classGraph) {
        return classFile.getInterfaceNames().stream()
                .flatMap(interfaceName -> {
                    if (JavaTypeUtils.mayBeJavaStdLibType(interfaceName)) {
                        return Stream.of(javaClassOrNothing(interfaceName));
                    }
                    var def = classGraph.findTypeDefinition(interfaceName);
                    if (def == null) return Stream.empty();
                    return findJavaSuperInterfaces(def.classFile, classGraph);
                });
    }

    private static Class<?> findJavaSuperType(ClassFile classFile, ClassGraph classGraph) {
        while (true) {
            var superType = classFile.getSuperClass();
            if (JavaTypeUtils.mayBeJavaStdLibType(superType)) {
                return javaClassOrNothing(superType);
            }
            var type = classGraph.findTypeDefinition(superType);
            if (type == null) return null;
            classFile = type.classFile;
        }
    }

    private static Class<?> javaClassOrNothing(String name) {
        try {
            return Class.forName(JavaTypeUtils.typeNameToClassName(name));
        } catch (ClassNotFoundException e) {
            throw new JBuildException("Cannot find Java stdlib class " + name, ACTION_ERROR);
        }
    }
}
