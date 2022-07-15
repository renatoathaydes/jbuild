package jbuild.util;

import jbuild.errors.JBuildException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.joining;
import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;

/**
 * Utility class to help handle Java types.
 */
public final class JavaTypeUtils {

    // types that should not be used in usual JVM projects, but are widely used so we open an exception
    // for them so usual jars like guava and Groovy won't cause errors
    private static final Set<String> WHITELISTED_NON_STDLIB_TYPES = Set.of(
            "Lsun/misc/Unsafe;"
    );

    // See https://docs.oracle.com/en/java/javase/11/docs/api/overview-tree.html
    private static final Set<String> JAVA_STD_LIB_PACKAGES = Set.of(
            "Ljava/",
            "Ljavax/",
            "Lcom/sun/",
            "Ljdk/",
            "Lnetscape/javascript/",
            "Lorg/ietf/jgss/",
            "Lorg/w3c/dom/",
            "Lorg/xml/sax/"
    );

    /**
     * Convert a class name in the Java language conventional syntax into a JVM internal type name.
     * <p>
     * Note: JBuild uses the JVM internal type names for all types.
     *
     * @param className Java language type name
     * @return JVM internal class name
     */
    public static String classNameToTypeName(String className) {
        if (className.startsWith("\"")
                // avoid converting already converted type names
                || (isReferenceType(className))
        ) return className;
        var result = new StringBuilder(className.length() + 4);
        if (className.endsWith("]")) {
            var firstBracket = className.indexOf('[');
            for (int i = 0; i < className.length() - firstBracket; i += 2) {
                result.append('[');
            }
            className = className.substring(0, firstBracket);
        }
        return result.append(typeName(className)).toString();
    }

    private static String typeName(String basicClassName) {
        switch (basicClassName) {
            case "byte":
                return "B";
            case "char":
                return "C";
            case "double":
                return "D";
            case "float":
                return "F";
            case "int":
                return "I";
            case "long":
                return "J";
            case "short":
                return "S";
            case "void":
                return "V";
            case "boolean":
                return "Z";
            default:
                return "L" + basicClassName.replaceAll("\\.", "/") + ';';
        }
    }

    /**
     * Convert a JVM internal type name to a name using the Java language conventional syntax.
     *
     * @param typeName JVM internal class name
     * @return Java language type name
     */
    public static String typeNameToClassName(String typeName) {
        var typeInfo = TypeInfo.from(typeName);
        var simpleClassName = className(typeInfo.basicTypeName, typeInfo.isReferenceType);
        if (typeInfo.arrayDimensions == 0) return simpleClassName;
        return simpleClassName + arrayTypeSuffix(typeInfo.arrayDimensions);
    }

    private static CharSequence arrayTypeSuffix(int dimensions) {
        switch (dimensions) {
            case 1:
                return "[]";
            case 2:
                return "[][]";
            default:
                return "[]".repeat(dimensions);
        }
    }

    private static String className(String basicTypeName, boolean isReferenceType) {
        if (isReferenceType) {
            return basicTypeName.substring(1, basicTypeName.length() - 1).replaceAll("/", ".");
        }
        switch (basicTypeName.charAt(0)) {
            case 'B':
                return "byte";
            case 'C':
                return "char";
            case 'D':
                return "double";
            case 'F':
                return "float";
            case 'I':
                return "int";
            case 'J':
                return "long";
            case 'S':
                return "short";
            case 'V':
                return "void";
            case 'Z':
                return "boolean";
            default:
                return basicTypeName;
        }
    }

    /**
     * Check if the given JVM internal type name is a reference type.
     *
     * @param typeName name of type
     * @return true if type name starts with 'L' and ends with ';', false otherwise.
     */
    public static boolean isReferenceType(String typeName) {
        var startIndex = 0;
        for (char c : typeName.toCharArray()) {
            if (c == '[') startIndex++;
            else break;
        }
        return typeName.substring(startIndex).startsWith("L") && typeName.endsWith(";");
    }

    /**
     * Clean a type name in case it's an array type, so that the result is a simple type name.
     *
     * @param type name
     * @return non-array type name
     */
    public static String cleanArrayTypeName(String type) {
        if (type.startsWith("\"[") || type.startsWith("[")) {
            var index = type.lastIndexOf('[');
            var end = type.endsWith("\"") ? type.length() - 1 : type.length();
            return type.substring(index + 1, end);
        }
        return type;
    }

    /**
     * Parse a list of types from a method argument list type descriptor.
     * <p>
     * This method is intended to be used to find reference to types, hence it drops array components from type
     * descriptors, i.e. if a type is referred to as an array like {@code [Ljava/lang/Object;}, this method will
     * return the plain type {@code Ljava/lang/Object;}.
     *
     * @param typeDef type definition
     * @return the plain types included in the type definition
     */
    public static List<String> parseMethodArgumentsTypes(String typeDef) {
        if (typeDef.startsWith("(")) {
            typeDef = typeDef.substring(1);
        }
        if (typeDef.endsWith(")")) {
            typeDef = typeDef.substring(0, typeDef.length() - 1);
        }
        return parseTypeList(typeDef.toCharArray());
    }

    private static List<String> parseTypeList(char[] chars) {
        int index = 0;
        var result = new ArrayList<String>(4);

        while (index < chars.length) {
            // Primitive types: B C D F I J S Z (V for void)
            // Reference types: L<name>;
            switch (chars[index]) {
                case 'B':
                case 'C':
                case 'D':
                case 'F':
                case 'I':
                case 'J':
                case 'S':
                case 'V':
                case 'Z': {
                    result.add(Character.toString(chars[index]));
                    index++;
                    break;
                }
                case '[': {
                    // array types are ignored
                    index++;
                    break;
                }
                case 'L': {
                    var typeEnd = find(';', chars, index);
                    if (typeEnd < 0) {
                        throw new JBuildException(
                                "Invalid Java type descriptor, reference type does not end with ';'",
                                ACTION_ERROR);

                    }
                    var type = toString(chars, index, typeEnd);
                    result.add(type);
                    index += type.length();
                    break;
                }
                default:
                    throw new JBuildException(
                            "Invalid Java type descriptor, not primitive or reference type: " + chars[index],
                            ACTION_ERROR);
            }
        }

        return result;
    }

    /**
     * Check if a type may be part of the Java standard library.
     * <p>
     * See <a href="https://docs.oracle.com/en/java/javase/11/docs/api/overview-tree.html">JDK Javadocs</a>
     * for a complete list of packages.
     *
     * @param typeName name of type
     * @return true if the type belongs to one of the packages included in the Java standard library.
     */
    public static boolean mayBeJavaStdLibType(String typeName) {
        return WHITELISTED_NON_STDLIB_TYPES.contains(typeName) ||
                JAVA_STD_LIB_PACKAGES.stream().anyMatch(typeName::startsWith);
    }

    /**
     * Check if a type refers to a Java primitive type or {@code void}.
     *
     * @param typeName name of type
     * @return true if the type is primitive, false otherwise.
     */
    public static boolean isPrimitiveJavaType(String typeName) {
        var type = cleanArrayTypeName(typeName);
        if (type.length() > 1) return false;
        switch (type.charAt(0)) {
            case 'B':
            case 'C':
            case 'D':
            case 'F':
            case 'I':
            case 'J':
            case 'S':
            case 'V':
            case 'Z':
                return true;
            default:
                return false;
        }
    }

    private static String toString(char[] chars, int start, int end) {
        return new String(chars, start, end - start + 1);
    }

    private static int find(char c, char[] chars, int index) {
        for (int i = index; i < chars.length; i++) {
            if (chars[i] == c) return i;
        }
        return -1;
    }

    /**
     * Convert a {@link Class} to its type name / descriptor.
     *
     * @param type Java type
     * @return the type name / descriptor.
     */
    public static String toTypeDescriptor(Class<?> type) {
        var name = type.getName();
        switch (name) {
            case "byte":
                return "B";
            case "char":
                return "C";
            case "double":
                return "D";
            case "float":
                return "F";
            case "int":
                return "I";
            case "long":
                return "J";
            case "short":
                return "S";
            case "boolean":
                return "Z";
            case "void":
                return "V";
        }

        return classNameToTypeName(type.getName());
    }

    /**
     * Convert a method's return types and parameter types to the equivalent type name / descriptor.
     *
     * @param returnType     return type
     * @param parameterTypes method parameter types
     * @return the type name / descriptor.
     */
    public static String toMethodTypeDescriptor(Class<?> returnType,
                                                Class<?>... parameterTypes) {
        return Arrays.stream(parameterTypes).map(JavaTypeUtils::toTypeDescriptor)
                .collect(joining("", "(", ")")) + toTypeDescriptor(returnType);
    }

    private static final class TypeInfo {
        final String basicTypeName;
        final int arrayDimensions;
        final boolean isReferenceType;

        public TypeInfo(String basicTypeName, int arrayDimensions, boolean isReferenceType) {
            this.basicTypeName = basicTypeName;
            this.arrayDimensions = arrayDimensions;
            this.isReferenceType = isReferenceType;
        }

        static TypeInfo from(String typeName) {
            var arrayDimensions = 0;
            for (char c : typeName.toCharArray()) {
                if (c == '[') arrayDimensions++;
                else break;
            }
            var isReferenceType = typeName.charAt(arrayDimensions) == 'L';
            return new TypeInfo(typeName.substring(arrayDimensions), arrayDimensions, isReferenceType);
        }
    }

}
