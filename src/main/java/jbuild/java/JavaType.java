package jbuild.java;

import java.util.List;

public final class JavaType {

    public static final TypeBound OBJECT = new TypeBound("Ljava/lang/Object;", List.of());

    public String name;
    public TypeBound superType;
    public List<TypeParam> typeParameters;
    public List<TypeBound> interfaces;

    public JavaType(String name,
                    TypeBound superType,
                    List<TypeParam> typeParameters,
                    List<TypeBound> interfaces) {
        this.name = name;
        this.superType = superType;
        this.typeParameters = typeParameters;
        this.interfaces = interfaces;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JavaType javaType = (JavaType) o;

        if (!name.equals(javaType.name)) return false;
        if (!superType.equals(javaType.superType)) return false;
        if (!typeParameters.equals(javaType.typeParameters)) return false;
        return interfaces.equals(javaType.interfaces);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + superType.hashCode();
        result = 31 * result + typeParameters.hashCode();
        result = 31 * result + interfaces.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "JavaType{" +
                "name='" + name + '\'' +
                ", superType=" + superType +
                ", typeParams=" + typeParameters +
                ", interfaces=" + interfaces +
                '}';
    }

    public static final class TypeParam {
        public final String name;
        public final List<TypeBound> bounds;

        public TypeParam(String name,
                         List<TypeBound> bounds) {
            this.name = name;
            this.bounds = bounds;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TypeParam typeSpec = (TypeParam) o;

            if (!name.equals(typeSpec.name)) return false;
            return bounds.equals(typeSpec.bounds);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + bounds.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "TypeParam{" +
                    "name='" + name + '\'' +
                    ", bounds=" + bounds +
                    '}';
        }
    }

    public static final class TypeBound {
        public final String name;
        public final List<TypeParam> params;

        public TypeBound(String name,
                         List<TypeParam> params) {
            this.name = name;
            this.params = params;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TypeBound typeBound = (TypeBound) o;

            if (!name.equals(typeBound.name)) return false;
            return params.equals(typeBound.params);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + params.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "TypeBound{" +
                    "name='" + name + '\'' +
                    ", params=" + params +
                    '}';
        }
    }

}
