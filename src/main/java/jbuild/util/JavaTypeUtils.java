package jbuild.util;

import jbuild.errors.JBuildException;

import java.util.ArrayList;
import java.util.List;

import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;

/**
 * Utility class to help handle Java types.
 */
public final class JavaTypeUtils {

    /**
     * Convert a class name in the Java language conventional syntax into a JVM internal type name.
     * <p>
     * Note: JBuild uses the JVM internal type names for all types.
     *
     * @param className Java language type name
     * @return JVM internal class name
     */
    public static String classNameToTypeName(String className) {
        // array type reference, leave it as it is
        if (className.startsWith("\"")
                // avoid converting already converted type names
                || (className.startsWith("L") && className.endsWith(";"))
        ) return className;

        return "L" + className.replaceAll("\\.", "/") + ";";
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

    private static String toString(char[] chars, int start, int end) {
        return new String(chars, start, end - start + 1);
    }

    private static int find(char c, char[] chars, int index) {
        for (int i = index; i < chars.length; i++) {
            if (chars[i] == c) return i;
        }
        return -1;
    }

}
