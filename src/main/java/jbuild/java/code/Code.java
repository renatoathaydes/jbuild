package jbuild.java.code;

import jbuild.util.Describable;

import java.util.function.Consumer;
import java.util.function.Function;

import static jbuild.util.JavaTypeUtils.typeNameToClassName;

/**
 * Code element that appears within method implementations.
 */
public abstract class Code implements Describable {

    /**
     * The name of the type which owns this code element.
     * <p>
     * In the case of a {@link Code.Type}, the name of the type itself.
     */
    public final String typeName;

    private Code(String typeName) {
        this.typeName = typeName;
    }

    public abstract <T> T match(Function<Type, T> type,
                                Function<Field, T> field,
                                Function<Method, T> method);

    public void use(Consumer<Type> onType,
                    Consumer<Field> onField,
                    Consumer<Method> onMethod) {
        match(t -> {
            onType.accept(t);
            return null;
        }, f -> {
            onField.accept(f);
            return null;
        }, m -> {
            onMethod.accept(m);
            return null;
        });
    }

    /**
     * Field access code.
     */
    public static final class Field extends Code {

        /**
         * JVM instructions for method invocations.
         */
        public enum Instruction {
            getstatic, putstatic, getfield, putfield, other;

            public boolean isStatic() {
                return this == getstatic || this == putstatic;
            }
        }

        /**
         * Field name.
         */
        public final String name;

        /**
         * Field type.
         */
        public final String type;

        public final Field.Instruction instruction;

        public Field(String typeName, String name, String type, Field.Instruction instruction) {
            super(typeName);
            this.name = name;
            this.type = type;
            this.instruction = instruction;
        }

        @Override
        public <T> T match(Function<Type, T> type,
                           Function<Field, T> field,
                           Function<Method, T> method) {
            return field.apply(this);
        }

        @Override
        public void describe(StringBuilder builder, boolean verbose) {
            builder.append(typeName).append(instruction.isStatic() ? ':' : '#')
                    .append(name).append("::").append(type);
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
                    ", instruction='" + instruction.name() + '\'' +
                    '}';
        }

        public Definition.FieldDefinition toDefinition() {
            return new Definition.FieldDefinition(name, type, instruction.isStatic());
        }
    }

    /**
     * A method invocation in code.
     */
    public static final class Method extends Code {

        /**
         * JVM instructions for method invocations.
         */
        public enum Instruction {
            invokespecial, invokestatic, invokevirtual, invokeinterface, other;

            public boolean isStatic() {
                return this == invokestatic;
            }
        }

        /**
         * Method name.
         */
        public final String name;

        /**
         * Method type signature.
         * <p>
         * Use {@link Method#toDefinition()} to obtain the method's parameters and return type.
         */
        public final String type;

        /**
         * The JVM instruction used in the method invocation.
         */
        public final Method.Instruction instruction;

        public Method(String typeName, String name, String type) {
            this(typeName, name, type, Method.Instruction.other);
        }

        public Method(String typeName, String name, String type, Method.Instruction instruction) {
            super(typeName);
            this.name = name;
            this.type = type;
            this.instruction = instruction;
        }

        @Override
        public <T> T match(Function<Type, T> type,
                           Function<Field, T> field,
                           Function<Method, T> method) {
            return method.apply(this);
        }

        @Override
        public void describe(StringBuilder builder, boolean verbose) {
            builder.append(typeNameToClassName(typeName))
                    .append(instruction.isStatic() ? ':' : '#');
            toDefinition().describe(builder, verbose);
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
                    ", instruction='" + instruction + '\'' +
                    '}';
        }

        /**
         * @return this {@link Method} converted to a {@link Definition}.
         */
        public Definition.MethodDefinition toDefinition() {
            return new Definition.MethodDefinition(name, type, instruction == Method.Instruction.invokestatic);
        }
    }

    /**
     * A type usage (cast or check) in code.
     */
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
        public void describe(StringBuilder builder, boolean verbose) {
            builder.append(typeName);
        }

        @Override
        public String getDescription() {
            return typeName;
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
