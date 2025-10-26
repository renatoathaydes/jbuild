package jbuild.commands;

import jbuild.api.JBuildException;
import jbuild.classes.model.ClassFile;
import jbuild.java.ClassGraph;
import jbuild.util.JavaTypeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static jbuild.api.JBuildException.ErrorCause.ACTION_ERROR;

final class JavaDescriptorsCache {

    static Stream<String> findFieldDescriptorsByName(
            ClassFile classFile,
            String targetName,
            ClassGraph classGraph) {
        return findFieldDescriptors(expandWithJavaSuperTypes(classFile, classGraph), targetName);
    }

    static Stream<String> findMethodDescriptorsByName(
            Collection<ClassFile> classFiles,
            String targetName,
            ClassGraph classGraph) {
        return findMethodDescriptors(
                classFiles.stream().flatMap(classFile ->
                        expandWithJavaSuperTypes(classFile, classGraph)),
                targetName);
    }

    static Set<ClassFile> expandWithSuperTypes(ClassFile classFile, ClassGraph classGraph) {
        var result = new HashSet<ClassFile>();
        var toVisit = new ArrayList<ClassFile>();
        toVisit.add(classFile);
        while (!toVisit.isEmpty()) {
            var nextIteration = List.copyOf(toVisit);
            toVisit.clear();
            for (var cf : nextIteration) {
                result.add(cf);
                var superDef = classGraph.findTypeDefinition(cf.getSuperClass());
                if (superDef != null) {
                    toVisit.add(superDef.classFile);
                }
                for (var inter : cf.getInterfaceNames()) {
                    var interDef = classGraph.findTypeDefinition(inter);
                    if (interDef != null) {
                        toVisit.add(interDef.classFile);
                    }
                }
            }
        }
        return result;
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
                findJavaSuperInterfaces(classFile, classGraph, superClass));
    }

    private static Stream<Class<?>> findJavaSuperInterfaces(ClassFile classFile,
                                                            ClassGraph classGraph,
                                                            Class<?> superClass) {
        return Stream.concat(
                superClass == null ? Stream.empty() : Arrays.stream(superClass.getInterfaces()),
                classFile.getInterfaceNames().stream()
                        .flatMap(interfaceName -> {
                            if (JavaTypeUtils.mayBeJavaStdLibType(interfaceName)) {
                                return Stream.of(javaClassOrNothing(interfaceName));
                            }
                            var def = classGraph.findTypeDefinition(interfaceName);
                            if (def == null) return Stream.empty();
                            return findJavaSuperInterfaces(def.classFile, classGraph, null);
                        }));
    }

    private static Class<?> findJavaSuperType(ClassFile classFile, ClassGraph classGraph) {
        while (true) {
            var superType = classFile.getSuperClass();
            if (JavaTypeUtils.mayBeJavaStdLibType(superType)) {
                return javaClassOrNothing(superType);
            }
            var type = classGraph.findTypeDefinition(superType);
            if (type == null) return Object.class;
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

    static Stream<String> findArrayFieldDescriptorsByName(String name) {
        return findFieldDescriptors(Stream.of(Object.class, DummyArray.class), name);
    }

    static Stream<String> findArrayMethodDescriptorsByName(String name) {
        return findMethodDescriptors(Stream.of(Object.class, DummyArray.class), name);
    }

    private static Stream<String> findFieldDescriptors(Stream<Class<?>> classes, String name) {
        return classes.flatMap(c -> findAccessibleFieldDescriptors(c, name));
    }

    private static Stream<String> findMethodDescriptors(Stream<Class<?>> classes, String name) {
        return classes.flatMap(c -> findAccessibleMethodDescriptors(c, name));
    }

    // TODO follow Java visibility rules
    private static Stream<String> findAccessibleFieldDescriptors(Class<?> type, String name) {
        var toVisit = new ArrayList<Class<?>>();
        toVisit.add(type);
        var result = new HashSet<String>(4);
        while (!toVisit.isEmpty()) {
            var currentType = toVisit.remove(0);
            for (var f : currentType.getDeclaredFields()) {
                if (f.getName().equals(name)) {
                    result.add(JavaTypeUtils.toTypeDescriptor(f.getType()));
                }
            }
            var superType = currentType.getSuperclass();
            if (superType != null) {
                toVisit.add(superType);
            }
            for (Class<?> inter : currentType.getInterfaces()) {
                toVisit.add(inter);
            }
        }
        return result.stream();
    }

    private static Stream<String> findAccessibleMethodDescriptors(Class<?> type, String name) {
        var toVisit = new ArrayList<Class<?>>();
        toVisit.add(type);
        var result = new HashSet<String>(4);
        while (!toVisit.isEmpty()) {
            var currentType = toVisit.remove(0);
            for (var m : currentType.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    result.add(JavaTypeUtils.toMethodTypeDescriptor(m.getReturnType(), m.getParameterTypes()));
                }
            }
            var superType = currentType.getSuperclass();
            if (superType != null) {
                toVisit.add(superType);
            }
            for (Class<?> inter : currentType.getInterfaces()) {
                toVisit.add(inter);
            }
        }
        return result.stream();
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
