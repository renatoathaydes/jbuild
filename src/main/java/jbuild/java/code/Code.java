package jbuild.java.code;

import java.util.function.Function;

public abstract class Code {

    public final String typeName;

    private Code(String typeName) {
        this.typeName = typeName;
    }

    public abstract <T> T match(Function<Type, T> type,
                                Function<Field, T> field,
                                Function<Method, T> method);

    public static final class Field extends Code {

        public final String name;
        public final String type;

        public Field(String typeName, String name, String type) {
            super(typeName);
            this.name = name;
            this.type = type;
        }

        @Override
        public <T> T match(Function<Type, T> type,
                           Function<Field, T> field,
                           Function<Method, T> method) {
            return field.apply(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Field field = (Field) o;

            if (!typeName.equals(field.typeName)) return false;
            if (!name.equals(field.name)) return false;
            return type.equals(field.type);
        }

        @Override
        public int hashCode() {
            int result = typeName.hashCode();
            result = 31 * result + name.hashCode();
            result = 31 * result + type.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "Field{" +
                    "typeName='" + typeName + '\'' +
                    ", name='" + name + '\'' +
                    ", type='" + type + '\'' +
                    '}';
        }
    }

    public static final class Method extends Code {

        public final String name;
        public final String type;

        public Method(String typeName, String name, String type) {
            super(typeName);
            this.name = name;
            this.type = type;
        }

        @Override
        public <T> T match(Function<Type, T> type,
                           Function<Field, T> field,
                           Function<Method, T> method) {
            return method.apply(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Method method = (Method) o;

            if (!typeName.equals(method.typeName)) return false;
            if (!name.equals(method.name)) return false;
            return type.equals(method.type);
        }

        @Override
        public int hashCode() {
            int result = typeName.hashCode();
            result = 31 * result + name.hashCode();
            result = 31 * result + type.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "Method{" +
                    "typeName='" + typeName + '\'' +
                    ", name='" + name + '\'' +
                    ", type='" + type + '\'' +
                    '}';
        }

        public Definition.MethodDefinition toDefinition() {
            return new Definition.MethodDefinition(name, type);
        }
    }

    public static final class Type extends Code {

        public Type(String typeName) {
            super(typeName);
        }

        @Override
        public <T> T match(Function<Type, T> type,
                           Function<Field, T> field,
                           Function<Method, T> method) {
            return type.apply(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Type type = (Type) o;

            return typeName.equals(type.typeName);
        }

        @Override
        public int hashCode() {
            return typeName.hashCode();
        }

        @Override
        public String toString() {
            return "Type{" +
                    "name='" + typeName + '\'' +
                    '}';
        }
    }

}
