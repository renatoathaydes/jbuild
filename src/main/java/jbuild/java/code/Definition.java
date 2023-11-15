package jbuild.java.code;

import jbuild.util.Describable;

import java.util.List;
import java.util.function.Function;

import static jbuild.util.JavaTypeUtils.parseMethodTypeRefs;
import static jbuild.util.JavaTypeUtils.typeNameToClassName;

/**
 * A definition within a type (can be a field or a method).
 */
public abstract class Definition implements Describable {

    public final String name;
    public final String type;
    public final boolean isStatic;

    private Definition(String name, String type, boolean isStatic) {
        this.name = name;
        this.type = type;
        this.isStatic = isStatic;
    }

    public abstract <T> T match(Function<FieldDefinition, T> matchField,
                                Function<MethodDefinition, T> matchMethod);

    @Override
    public void describe(StringBuilder builder, boolean verbose) {
        builder.append(name).append("::").append(typeNameToClassName(type));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Definition that = (Definition) o;

        if (isStatic != that.isStatic) return false;
        if (!name.equals(that.name)) return false;
        return type.equals(that.type);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + (isStatic ? 1 : 0);
        return result;
    }

    /**
     * A definition of a field.
     */
    public static final class FieldDefinition extends Definition {

        public FieldDefinition(String name, String type, boolean isStatic) {
            super(name, type, isStatic);
        }

        public FieldDefinition(String name, String type) {
            super(name, type, false);
        }

        @Override
        public <T> T match(Function<FieldDefinition, T> matchField,
                           Function<MethodDefinition, T> matchMethod) {
            return matchField.apply(this);
        }

        @Override
        public String toString() {
            return "FieldDefinition{" +
                    "name='" + name + '\'' +
                    ", type='" + type + '\'' +
                    ", isStatic='" + isStatic + '\'' +
                    '}';
        }
    }

    /**
     * A definition of a method.
     */
    public static final class MethodDefinition extends Definition {

        // cache return type and parameter types on first usage
        private String returnType;
        private List<String> parameterTypes;

        public MethodDefinition(String name, String type, boolean isStatic) {
            super(name, type, isStatic);
        }

        public MethodDefinition(String name, String type) {
            this(name, type, false);
        }

        @Override
        public <T> T match(Function<FieldDefinition, T> matchField,
                           Function<MethodDefinition, T> matchMethod) {
            return matchMethod.apply(this);
        }

        public boolean isConstructor() {
            // the only method that has the syntax of a reference type is a constructor
            return name.startsWith("L") && name.endsWith(";");
        }

        public String getReturnType() {
            if (returnType == null) {
                var paramsCloseIndex = type.indexOf(')');
                if (paramsCloseIndex < 0 || paramsCloseIndex + 1 >= type.length()) {
                    returnType = "V";
                } else {
                    returnType = type.substring(paramsCloseIndex + 1);
                }
            }
            return returnType;
        }

        public List<String> getParameterTypes() {
            if (parameterTypes == null) {
                var paramsCloseIndex = type.indexOf(')');
                if (paramsCloseIndex < 0) {
                    parameterTypes = List.of();
                } else {
                    parameterTypes = parseMethodTypeRefs(type.substring(0, paramsCloseIndex));
                }
            }
            return parameterTypes;
        }

        @Override
        public void describe(StringBuilder builder, boolean verbose) {
            builder.append(name).append('(');
            var paramCount = getParameterTypes().size();
            for (var i = 0; i < paramCount; ) {
                String parameterType = parameterTypes.get(i);
                builder.append(typeNameToClassName(parameterType));
                if (++i < paramCount) {
                    builder.append(", ");
                }
            }
            builder.append(")::").append(typeNameToClassName(getReturnType()));
        }

        @Override
        public String toString() {
            return "MethodDefinition{" +
                    "name='" + name + '\'' +
                    ", type='" + type + '\'' +
                    ", isStatic='" + isStatic + '\'' +
                    '}';
        }
    }
}
