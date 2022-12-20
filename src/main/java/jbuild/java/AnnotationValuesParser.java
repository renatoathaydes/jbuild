package jbuild.java;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jbuild.errors.JBuildException;
import jbuild.errors.JBuildException.ErrorCause;
import jbuild.java.code.AnnotationValues;

final class AnnotationValuesParser {

    public Collection<AnnotationValues> parse(Iterator<String> lines) {
        if (!lines.hasNext()) {
            throw new JBuildException("Unexpected end of file",
                    ErrorCause.ACTION_ERROR);
        }
        var line = lines.next();
        if (!isIndexLine(line)) {
            throw new JBuildException("Not annotation index line: '" + line + "'",
                    ErrorCause.ACTION_ERROR);

        }
        var result = new ArrayList<AnnotationValues>(2);
        do {
            result.add(parseAnnotation(lines));
        } while (lines.hasNext() && isIndexLine(lines.next()));

        return result;
    }

    private static boolean isIndexLine(String line) {
        assert line.startsWith("  ");
        var colonIndex = line.indexOf(":", 2);
        if (colonIndex < 2) {
            return false;
        }
        try {
            Integer.parseInt(line.substring(2, colonIndex));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static AnnotationValues parseAnnotation(Iterator<String> lines) {
        if (!lines.hasNext()) {
            throw new JBuildException("Unexpected end of file",
                    ErrorCause.ACTION_ERROR);
        }
        var line = lines.next();
        assert line.startsWith("    ") && line.endsWith("(");
        var type = line.substring(4, line.length() - 1);
        var values = parseValues(lines);
        return new AnnotationValues(type, values);
    }

    private static Map<String, Object> parseValues(Iterator<String> lines) {
        if (!lines.hasNext())
            return Map.of();
        var result = new LinkedHashMap<String, Object>(4);

        do {
            var line = lines.next();
            var parts = line.split("=", 2);
            if (parts.length == 2) {
                var key = parts[0].trim();
                var value = parts[1];
                result.put(key, parseValue(value, lines));
            } else if (line.trim().equals(")")) {
                return result;
            } else {
                throw new JBuildException("Unexpected annotation value: '" + line + "'",
                        ErrorCause.ACTION_ERROR);
            }
        } while (lines.hasNext());

        throw new JBuildException("Unexpected EOF", ErrorCause.ACTION_ERROR);

    }

    private static Object parseValue(String value, Iterator<String> lines) {
        if (value.startsWith("@")) { // sub-annotation
            assert value.endsWith("(");
            var name = value.substring(1, value.length() - 1);
            return new AnnotationValues(name, parseValues(lines));
        } else if (value.startsWith("[")) {
            assert value.endsWith("]"): value;
            return parseList(value.substring(1, value.length() - 1), lines);
        } else if (value.startsWith("\"")) {
            assert value.endsWith("\""): value;
            return value.substring(1, value.length() - 1);
        } else if (value.equals("true")) {
            return Boolean.TRUE;
        } else if (value.equals("false")) {
            return Boolean.FALSE;
        } else if (value.startsWith("'")) {
            assert value.endsWith("'"): value;
            assert value.length() == 3: value;
            return value.charAt(1);
        }
        return Double.parseDouble(value);
    }

    private static List<?> parseList(String value, Iterator<String> lines) {
        var result = new ArrayList<Object>();
        var startIndex = 0;
        var inString = false;
        var escape = false;
        var i = 0;
        for (; i < value.length(); i++) {
            var c = value.charAt(i);
            if (escape) {
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '\"') {
                if (inString) {
                    inString = false;
                } else {
                    inString = true;
                }
            } else if (!inString && c == ',') {
                result.add(parseValue(value.substring(startIndex, i), lines));
                startIndex = i + 1;
            }
        }
        if (startIndex < i) {
            result.add(parseValue(value.substring(startIndex), lines));
        }
        return result;
    }

}
