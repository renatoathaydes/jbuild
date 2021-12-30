package jbuild.java;

import java.util.List;

import static jbuild.util.CollectionUtils.append;

public final class JavaType {

    public enum Kind {
        CLASS, ENUM, INTERFACE,
    }

    public static final TypeBound OBJECT = new TypeBound("Ljava/lang/Object;", List.of());

    public String name;
    public Kind kind;
    public List<TypeBound> superTypes;
    public List<TypeParam> typeParameters;
    public List<TypeBound> interfaces;

    public JavaType(String name,
                    Kind kind,
                    List<TypeBound> superTypes,
                    List<TypeParam> typeParameters,
                    List<TypeBound> interfaces) {
        this.name = name;
        this.kind = kind;
        this.superTypes = superTypes;
        this.typeParameters = typeParameters;
        this.interfaces = interfaces;
    }

    public Iterable<TypeBound> getParentTypes() {
        return append(superTypes, interfaces);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JavaType javaType = (JavaType) o;

        if (!name.equals(javaType.name)) return false;
        if (!superTypes.equals(javaType.superTypes)) return false;
        if (!typeParameters.equals(javaType.typeParameters)) return false;
        return interfaces.equals(javaType.interfaces);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + superTypes.hashCode();
        result = 31 * result + typeParameters.hashCode();
        result = 31 * result + interfaces.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "JavaType{" +
                "name='" + name + '\'' +
                ", superTypes=" + superTypes +
                ", typeParams=" + typeParameters +
                ", interfaces=" + interfaces +
                '}';
    }

    public static final class TypeParam {
        public final String name;
        public final List<TypeBound> bounds;
        public final List<TypeParam> params;

        public TypeParam(String name,
                         List<TypeBound> bounds,
                         List<TypeParam> params) {
            this.name = name;
            this.bounds = bounds;
            this.params = params;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TypeParam typeParam = (TypeParam) o;

            if (!name.equals(typeParam.name)) return false;
            if (!bounds.equals(typeParam.bounds)) return false;
            return params.equals(typeParam.params);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + bounds.hashCode();
            result = 31 * result + params.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "TypeParam{" +
                    "name='" + name + '\'' +
                    ", bounds=" + bounds +
                    ", params=" + params +
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
