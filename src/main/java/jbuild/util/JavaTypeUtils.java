package jbuild.util;

import jbuild.errors.JBuildException;

import java.util.ArrayList;
import java.util.List;

import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;

public class JavaTypeUtils {

    public static List<String> parseTypes(String typeDef) {
        if (typeDef.startsWith("(")) {
            typeDef = typeDef.substring(1);
        }
        if (typeDef.endsWith(")")) {
            typeDef = typeDef.substring(0, typeDef.length() - 1);
        }
        return parseTypes(typeDef.toCharArray());
    }

    private static List<String> parseTypes(char[] chars) {
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
