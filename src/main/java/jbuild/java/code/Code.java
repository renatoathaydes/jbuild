package jbuild.java.code;

import java.util.function.Consumer;

public abstract class Code {

    private Code() {
    }

    public abstract void use(Consumer<Code.Field> field,
                             Consumer<Code.Method> method,
                             Consumer<Code.ClassRef> classRef);

    public static final class Field extends Code {
        public final String name;
        public final String type;

        public Field(String name, String type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public void use(Consumer<Field> field,
                        Consumer<Method> method,
                        Consumer<ClassRef> classRef) {
            field.accept(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Field field = (Field) o;

            if (!name.equals(field.name)) return false;
            return type.equals(field.type);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + type.hashCode();
            return result;
        }
    }

    public static final class Method extends Code {
        public final String className;
        public final String name;
        public final String type;

        public Method(String className, String name, String type) {
            this.className = className;
            this.name = name;
            this.type = type;
        }

        @Override
        public void use(Consumer<Field> field,
                        Consumer<Method> method,
                        Consumer<ClassRef> classRef) {
            method.accept(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Method method = (Method) o;

            if (!className.equals(method.className)) return false;
            if (!name.equals(method.name)) return false;
            return type.equals(method.type);
        }

        @Override
        public int hashCode() {
            int result = className.hashCode();
            result = 31 * result + name.hashCode();
            result = 31 * result + type.hashCode();
            return result;
        }
    }

    public static final class ClassRef extends Code {
        public final String name;

        public ClassRef(String name) {
            this.name = name;
        }

        @Override
        public void use(Consumer<Field> field,
                        Consumer<Method> method,
                        Consumer<ClassRef> classRef) {
            classRef.accept(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ClassRef classRef = (ClassRef) o;

            return name.equals(classRef.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

}
