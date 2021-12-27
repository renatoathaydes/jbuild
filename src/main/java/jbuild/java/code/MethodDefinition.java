package jbuild.java.code;

import java.util.List;

import static jbuild.util.JavaTypeUtils.parseTypes;

public final class MethodDefinition {

    public final String name;
    public final String type;

    // cache return type and parameter types on first usage
    private String returnType;
    private List<String> parameterTypes;

    public MethodDefinition(String name, String type) {
        this.name = name;
        this.type = type;
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
                parameterTypes = parseTypes(type.substring(0, paramsCloseIndex));
            }
        }
        return parameterTypes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MethodDefinition that = (MethodDefinition) o;

        if (!name.equals(that.name)) return false;
        return type.equals(that.type);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + type.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "MethodDefinition{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
