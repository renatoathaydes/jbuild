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
        return findFieldDescriptors(expandWithJavaSuperTypes(classFile, classGraph), targetName);
    }

    public static Stream<String> findMethodDescriptorsByName(
            ClassFile classFile,
            String targetName,
            ClassGraph classGraph) {
        return findMethodDescriptors(expandWithJavaSuperTypes(classFile, classGraph), targetName);
    }

    static Stream<ClassFile> expandWithSuperTypes(ClassFile classFile, ClassGraph classGraph) {
        return Stream.iterate(
                classFile,
                Objects::nonNull,
                cf -> {
                    var superClass = cf.getSuperClass();
                    var superType = classGraph.findTypeDefinition(superClass);
                    return superType == null ? null : superType.classFile;
                }
        ).flatMap(cf -> Stream.concat(
                Stream.of(cf),
                findSuperInterfaces(cf, classGraph)));
    }

    private static Stream<ClassFile> findSuperInterfaces(ClassFile classFile,
                                                         ClassGraph classGraph) {

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

    public static Stream<String> findArrayFieldDescriptorsByName(String name) {
        return findFieldDescriptors(Stream.of(Object.class, DummyArray.class), name);
    }

    public static Stream<String> findArrayMethodDescriptorsByName(String name) {
        return findMethodDescriptors(Stream.of(Object.class, DummyArray.class), name);
    }

    private static Stream<String> findFieldDescriptors(Stream<Class<?>> classes, String name) {
        return classes
                .flatMap(c -> Stream.of(c.getFields()))
                .filter(f -> f.getName().equals(name))
                .map(f -> JavaTypeUtils.toTypeDescriptor(f.getType()));
    }

    private static Stream<String> findMethodDescriptors(Stream<Class<?>> classes, String name) {
        return classes
                .flatMap(c -> Stream.of(c.getMethods()))
                .filter(m -> m.getName().equals(name))
                .map(m -> JavaTypeUtils.toMethodTypeDescriptor(m.getReturnType(), m.getParameterTypes()));
    }

    private static final class DummyArray {
        public final int length = 0;

        @SuppressWarnings("MethodDoesntCallSuperMethod")
        @Override
        public Object clone() {
            return this;
        }

    }
}
