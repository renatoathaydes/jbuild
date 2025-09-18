package jbuild.java;

import jbuild.classes.model.ClassFile;

public final class JavaType {

    public enum Kind {
        CLASS, ENUM, INTERFACE,
    }

    public static class TypeId {
        public final String name;
        public final Kind kind;

        public TypeId(String name, Kind kind) {
            this.name = name;
            this.kind = kind;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            TypeId typeId = (TypeId) o;
            return name.equals(typeId.name) && kind == typeId.kind;
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + kind.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "TypeId{" +
                    "name='" + name + '\'' +
                    ", kind=" + kind +
                    '}';
        }
    }

    public final TypeId typeId;
    public final ClassFile classFile;

    public JavaType(TypeId typeId, ClassFile classFile) {
        this.typeId = typeId;
        this.classFile = classFile;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        JavaType javaType = (JavaType) o;
        return typeId.equals(javaType.typeId) && classFile.equals(javaType.classFile);
    }

    @Override
    public int hashCode() {
        int result = typeId.hashCode();
        result = 31 * result + classFile.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "JavaType{" +
                "typeId=" + typeId +
                ", classFile=" + classFile +
                '}';
    }
}
