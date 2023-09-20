package jbuild.java;

import jbuild.classes.model.ClassFile;
import jbuild.java.code.TypeDefinition;

enum TypeDefinitionFactory {
    INSTANCE;

    TypeDefinition create(ClassFile classFile) {
        return new TypeDefinition(new JavaType(
                new JavaType.TypeId(classFile.getTypeName(), JavaType.Kind.CLASS),
                classFile.getInterfaceNames()));
    }
}
