package jbuild.classes.signature;

import java.util.List;

/**
 * A Java type signature represents either a reference type or a primitive type of the Java programming language.
 * <p>
 * See <a href="https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.7.9.1">JavaTypeSignature</a>.
 * <pre>
 * JavaTypeSignature:
 *   ReferenceTypeSignature
 *   BaseType
 * BaseType:
 *   (one of) B C D F I J S Z
 * </pre>
 */
public interface JavaTypeSignature {

    /**
     * Base types of the Java language (primitive types).
     */
    enum BaseType implements JavaTypeSignature {
        B("byte"), C("char"), D("double"), F("float"), I("int"), J("long"), S("short"), Z("boolean");

        /**
         * Java type name.
         */
        public final String name;

        BaseType(String name) {
            this.name = name;
        }

        /**
         * Pick the {@code BaseType} matching the given character.
         *
         * @param ch possibly representing a base type
         * @return the base type for the character, or null if it is not a base type
         */
        public static BaseType pickOrNull(char ch) {
            switch (ch) {
                case 'B':
                    return B;
                case 'C':
                    return C;
                case 'D':
                    return D;
                case 'F':
                    return F;
                case 'I':
                    return I;
                case 'J':
                    return J;
                case 'S':
                    return S;
                case 'Z':
                    return Z;
                default:
                    return null;
            }
        }

        @Override
        public String toString() {
            return "BaseType{" +
                    "name='" + name + '\'' +
                    '}';
        }
    }

    /**
     * A reference type signature represents a reference type of the Java programming language, that is,
     * a class or interface type, a type variable, or an array type.
     *
     * <pre>
     * ReferenceTypeSignature:
     *   ClassTypeSignature
     *   TypeVariableSignature
     *   ArrayTypeSignature
     * ClassTypeSignature:
     *   L [PackageSpecifier] SimpleClassTypeSignature {ClassTypeSignatureSuffix} ;
     * PackageSpecifier:
     *   Identifier / {PackageSpecifier}
     * ClassTypeSignatureSuffix:
     *   . SimpleClassTypeSignature
     * TypeVariableSignature:
     *   T Identifier ;
     * ArrayTypeSignature:
     *   [ JavaTypeSignature
     * </pre>
     */
    interface ReferenceTypeSignature extends JavaTypeSignature {

        /**
         * A class type signature represents a (possibly parameterized) class or interface type.
         */
        final class ClassTypeSignature implements ReferenceTypeSignature {
            public final String packageName;
            public final SimpleClassTypeSignature typeSignature;
            public final List<SimpleClassTypeSignature> typeSignatureSuffix;

            public ClassTypeSignature(String packageName,
                                      SimpleClassTypeSignature typeSignature,
                                      List<SimpleClassTypeSignature> typeSignatureSuffix) {
                this.packageName = packageName;
                this.typeSignature = typeSignature;
                this.typeSignatureSuffix = typeSignatureSuffix;
            }

            public ClassTypeSignature(String packageName,
                                      SimpleClassTypeSignature typeSignature) {
                this(packageName, typeSignature, List.of());
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                ClassTypeSignature that = (ClassTypeSignature) o;

                if (!packageName.equals(that.packageName)) return false;
                if (!typeSignature.equals(that.typeSignature)) return false;
                return typeSignatureSuffix.equals(that.typeSignatureSuffix);
            }

            @Override
            public int hashCode() {
                int result = packageName.hashCode();
                result = 31 * result + typeSignature.hashCode();
                result = 31 * result + typeSignatureSuffix.hashCode();
                return result;
            }

            @Override
            public String toString() {
                return "ClassTypeSignature{" +
                        "packageName='" + packageName + '\'' +
                        ", typeSignature=" + typeSignature +
                        ", typeSignatureSuffix=" + typeSignatureSuffix +
                        '}';
            }
        }

        final class TypeVariableSignature implements ReferenceTypeSignature {
            public final String identifier;

            public TypeVariableSignature(String identifier) {
                this.identifier = identifier;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                TypeVariableSignature that = (TypeVariableSignature) o;

                return identifier.equals(that.identifier);
            }

            @Override
            public int hashCode() {
                return identifier.hashCode();
            }

            @Override
            public String toString() {
                return "TypeVariableSignature{" +
                        "identifier='" + identifier + '\'' +
                        '}';
            }
        }

        final class ArrayTypeSignature implements ReferenceTypeSignature {
            public final short dimensions;
            public final JavaTypeSignature typeSignature;

            public ArrayTypeSignature(short dimensions, JavaTypeSignature typeSignature) {
                this.dimensions = dimensions;
                this.typeSignature = typeSignature;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                ArrayTypeSignature that = (ArrayTypeSignature) o;

                if (dimensions != that.dimensions) return false;
                return typeSignature.equals(that.typeSignature);
            }

            @Override
            public int hashCode() {
                int result = dimensions;
                result = 31 * result + typeSignature.hashCode();
                return result;
            }

            @Override
            public String toString() {
                return "ArrayTypeSignature{" +
                        "dimensions=" + dimensions +
                        ", typeSignature=" + typeSignature +
                        '}';
            }
        }

    }

}
