package jbuild.java;

import java.util.List;
import java.util.stream.Stream;

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
    }

    public static final TypeBound OBJECT = new TypeBound("Ljava/lang/Object;", List.of());

    public final TypeId typeId;
    public final List<TypeBound> superTypes;
    public final List<TypeParam> typeParameters;
    public final List<TypeBound> interfaces;

    public JavaType(TypeId typeId,
                    List<TypeBound> superTypes,
                    List<TypeParam> typeParameters,
                    List<TypeBound> interfaces) {
        this.typeId = typeId;
        this.superTypes = superTypes;
        this.typeParameters = typeParameters;
        this.interfaces = interfaces;
    }

    /**
     * @return the super types and interfaces implemented by this type.
     */
    public Iterable<TypeBound> getParentTypes() {
        var iter = parentTypes().iterator();
        return () -> iter;
    }

    private Stream<TypeBound> parentTypes() {
        return Stream.concat(superTypes.stream(), interfaces.stream());
    }

    /**
     * @return all other types referred to from the type signature of this type. This includes its super-type,
     * interfaces and type parameters' bounds, recursively.
     */
    public Stream<String> typesReferredTo() {
        return Stream.concat(
                typeParameters.stream().flatMap(TypeParam::typesReferredTo),
                parentTypes().flatMap(TypeBound::typesReferredTo));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JavaType javaType = (JavaType) o;

        if (!typeId.name.equals(javaType.typeId.name)) return false;
        if (!superTypes.equals(javaType.superTypes)) return false;
        if (!typeParameters.equals(javaType.typeParameters)) return false;
        return interfaces.equals(javaType.interfaces);
    }

    @Override
    public int hashCode() {
        int result = typeId.name.hashCode();
        result = 31 * result + superTypes.hashCode();
        result = 31 * result + typeParameters.hashCode();
        result = 31 * result + interfaces.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "JavaType{" +
                "name='" + typeId.name + '\'' +
                ", kind='" + typeId.kind + '\'' +
                ", superTypes=" + superTypes +
                ", typeParams=" + typeParameters +
                ", interfaces=" + interfaces +
                '}';
    }

    public static final class TypeParam {
        public final String name;
        public final List<TypeBound> bounds;
        public final List<TypeParam> params;
        public boolean isConcreteType;

        public TypeParam(String name,
                         List<TypeBound> bounds,
                         List<TypeParam> params) {
            this.name = name;
            this.bounds = bounds;
            this.params = params;
            this.isConcreteType = name.endsWith(";");
        }

        public Stream<String> typesReferredTo() {
            if (bounds.isEmpty() && params.isEmpty()) return Stream.of();
            return Stream.concat(
                    bounds.stream().flatMap(TypeBound::typesReferredTo),
                    params.stream().filter(p -> p.isConcreteType).flatMap(TypeParam::typesReferredTo));
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

        public Stream<String> typesReferredTo() {
            return Stream.concat(Stream.of(name), params.stream().flatMap(TypeParam::typesReferredTo));
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
