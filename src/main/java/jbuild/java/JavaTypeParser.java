package jbuild.java;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.lang.Character.isAlphabetic;
import static java.lang.Character.isDigit;
import static jbuild.util.JavaTypeUtils.classNameToTypeName;

/**
 * A parser that can identify and parse a type identifier line given {@link Tools.Javap}'s output.
 * <p>
 * This class helps {@link JavapOutputParser} to find types in the javap tool's output.
 * <p>
 * Because it is in the "hot path" when parsing javap output, this class was designed to be fast when given lots
 * of invalid lines to parse... for this reason, instead of throwing Exceptions or returning errors, it will simply
 * return null as soon as it finds out that a particular line is not a valid type identifier line. That means that
 * this should not be used as a general purpose parser as it won't give the reason why it failed to parse a line at all.
 */
public final class JavaTypeParser {

    private static final Set<String> MODIFIERS = Set.of("private", "public", "protected", "abstract", "final");
    private static final Set<String> TYPE_KIND = Set.of("class", "interface", "enum");

    private int index;
    private String line;

    public JavaType parse(String line) {
        this.line = line;
        this.index = 0;
        return parseType();
    }

    private JavaType parseType() {
        String name = null;
        JavaType.TypeBound superType = JavaType.OBJECT;
        List<JavaType.TypeParam> typeParameters = List.of();
        List<JavaType.TypeBound> interfaces = List.of();

        while (index >= 0 && index < line.length()) {
            var word = nextWord(false);
            if (word.isEmpty() || index >= line.length() || currentChar() != ' ') {
                return null;
            }
            index++; // go past the space
            if (MODIFIERS.contains(word)) {
                continue;
            }
            if (TYPE_KIND.contains(word)) {
                var spec = nextTypeBound();
                if (spec == null) return null;
                name = spec.name;
                typeParameters = spec.params;
                if (expectNext(" extends ")) {
                    var bound = nextTypeBound();
                    if (bound == null) return null;
                    if (!bound.equals(JavaType.OBJECT)) {
                        superType = bound;
                    }
                } else if (index < line.length()) {
                    System.out.println("expected extends or param, got " + currentChar());
                    return null;
                }
                break;
            } else {
                return null;
            }
        }

        if (name == null) return null;

        if (expectNext(" implements ")) {
            interfaces = parseBounds();
        } else if (index < line.length()) {
            System.out.println("did not finish parsing all line: " + line.substring(index));
            return null;
        }

        return new JavaType(name, superType, typeParameters, interfaces);
    }

    private JavaType.TypeBound nextTypeBound() {
        String name;
        List<JavaType.TypeParam> params = List.of();

        var word = nextWord(false);
        if (word.isEmpty()) {
            System.out.println("Got empty word");
            return null;
        }

        name = classNameToTypeName(word);

        System.out.println("Got type name: " + name);

        char c = currentChar();
        if (c == '<') {
            index++;
            System.out.println("parsing type parameters");
            params = parseParams();
            System.out.println("Params: " + params);
            if (params == null) return null;
        }
        return new JavaType.TypeBound(name, params);
    }

    private JavaType.TypeParam nextTypeParam() {
        List<JavaType.TypeBound> bounds = List.of();

        var name = nextWord(true);
        if (name.isEmpty()) {
            System.out.println("Got empty word");
            return null;
        }

        System.out.println("Got type param: " + name);

        if (expectNext(" extends ")) {
            bounds = parseBounds();
            if (bounds == null) return null;
        }

        return new JavaType.TypeParam(name, bounds);
    }

    private List<JavaType.TypeParam> parseParams() {
        var params = new ArrayList<JavaType.TypeParam>(2);
        var keepGoing = true;
        while (keepGoing) {
            var param = nextTypeParam();
            if (param == null) return null;
            params.add(param);
            keepGoing = expectNext(", ");
        }
        if (currentChar() == '>') {
            index++;
            return params;
        }
        System.out.println("Unexpected char after params: " + currentChar());
        return null;
    }

    private List<JavaType.TypeBound> parseBounds() {
        var bounds = new ArrayList<JavaType.TypeBound>(2);
        var keepGoing = true;
        while (keepGoing) {
            var bound = nextTypeBound();
            if (bound == null) return null;
            if (!JavaType.OBJECT.equals(bound)) {
                bounds.add(bound);
            }
            keepGoing = expectNext(" & ");
        }
        return bounds;
    }

    private boolean expectNext(String text) {
        for (int i = 0; i < text.length(); i++) {
            var c = text.charAt(i);
            if (currentChar() == c) {
                index++;
            } else {
                return false;
            }
        }
        return true;
    }

    private char currentChar() {
        if (index < line.length()) return line.charAt(index);
        return Character.MIN_VALUE;
    }

    private String nextWord(boolean isTypeParameter) {
        var next = nextSeparator();
        if (index == next) {
            if (isTypeParameter && currentChar() == '?') {
                index++;
                return "?";
            }
            return "";
        }
        var word = line.substring(index, next);
        index = next;
        return word;
    }

    private int nextSeparator() {
        var i = index;
        for (; i < line.length(); i++) {
            var c = line.charAt(i);
            if (!isAlphabetic(c) && !isDigit(c) && c != '_' && c != '$' && c != '.') {
                return i;
            }
        }
        return i;
    }

}
