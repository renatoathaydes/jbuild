package jbuild.java.code;

import java.util.List;
import java.util.function.Function;

import static jbuild.util.JavaTypeUtils.parseMethodArgumentsTypes;

public abstract class Definition {

    public final String name;
    public final String type;

    private Definition(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public abstract <T> T match(Function<FieldDefinition, T> matchField,
                                Function<MethodDefinition, T> matchMethod);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Definition that = (Definition) o;

        if (!name.equals(that.name)) return false;
        return type.equals(that.type);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + type.hashCode();
        return result;
    }

    public static final class FieldDefinition extends Definition {

        public FieldDefinition(String name, String type) {
            super(name, type);
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
                    '}';
        }
    }

    public static final class MethodDefinition extends Definition {

        // cache return type and parameter types on first usage
        private String returnType;
        private List<String> parameterTypes;

        public MethodDefinition(String name, String type) {
            super(name, type);
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
                    parameterTypes = parseMethodArgumentsTypes(type.substring(0, paramsCloseIndex));
                }
            }
            return parameterTypes;
        }

        @Override
        public String toString() {
            return "MethodDefinition{" +
                    "name='" + name + '\'' +
                    ", type='" + type + '\'' +
                    '}';
        }
    }
}
