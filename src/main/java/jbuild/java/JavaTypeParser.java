package jbuild.java;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * of invalid lines to parse... for this reason, instead of throwing Exceptions or returning errors, when created with
 * {@code throwOnError} set to {@code false}, it will simply
 * return {@code null} as soon as it finds out that a particular line is not a valid type identifier line.
 */
public final class JavaTypeParser {

    private static final Set<String> MODIFIERS = Set.of("private", "public", "protected", "abstract", "final");
    private static final Set<String> TYPE_KIND = Set.of("class", "interface");

    private int index;
    private String line;

    private final boolean throwOnError;

    /**
     * Create a new {@link JavapOutputParser}.
     *
     * @param throwOnError whether to throw an Exception on errors, or return null silently.
     */
    public JavaTypeParser(boolean throwOnError) {
        this.throwOnError = throwOnError;
    }

    public JavaType parse(String line) {
        this.line = line;
        this.index = 0;
        return parseType();
    }

    private JavaType parseType() {
        String name = null;
        JavaType.Kind kind = null;
        List<JavaType.TypeBound> superTypes = List.of();
        List<JavaType.TypeParam> typeParameters = List.of();
        List<JavaType.TypeBound> interfaces = List.of();

        while (index >= 0 && index < line.length()) {
            var word = nextWord(false);
            if (word.isEmpty() || index >= line.length() || currentChar() != ' ') {
                if (throwOnError) throw new RuntimeException("expected word followed by space but got '" +
                        word + currentChar() + "'");
                return null;
            }
            index++; // go past the space
            if (MODIFIERS.contains(word)) {
                continue;
            }
            if (TYPE_KIND.contains(word)) {
                kind = JavaType.Kind.valueOf(word.toUpperCase(Locale.ROOT));
                var spec = nextTypeBound();
                if (spec == null) return null;
                name = spec.name;
                typeParameters = spec.params;
                if (expectNext(" extends ")) {
                    if (kind == JavaType.Kind.INTERFACE) {
                        interfaces = parseBounds(true);
                        if (interfaces == null) return null;
                    } else {
                        var bound = nextTypeBound();
                        if (bound == null) return null;
                        if (!bound.equals(JavaType.OBJECT)) {
                            superTypes = List.of(bound);
                        }
                    }
                    if (superTypes.stream().anyMatch(t -> t.name.equals("Ljava/lang/Enum;"))) {
                        kind = JavaType.Kind.ENUM;
                    }
                }
                break;
            } else {
                if (throwOnError) throw new RuntimeException("expected a type kind or modifier but got '" + word + "'");
                return null;
            }
        }

        if (name == null || name.isBlank()) {
            if (throwOnError) throw new RuntimeException("no type name found");
            return null;
        }

        if (kind != JavaType.Kind.INTERFACE &&
                (expectNext(" implements ") || expectNext("implements "))) {
            interfaces = parseBounds(true);
            if (interfaces == null) {
                return null;
            }
        }
        if (index < line.length()) {
            if (throwOnError)
                throw new RuntimeException("unexpected input following type at index " + index);
            return null;
        }

        return new JavaType(name, kind, superTypes, typeParameters, interfaces);
    }

    private JavaType.TypeBound nextTypeBound() {
        String name;
        List<JavaType.TypeParam> params = List.of();

        var word = nextWord(false);
        if (word.isEmpty()) {
            if (throwOnError) throw new RuntimeException("expected type bound but got '" + currentChar() + "'");
            return null;
        }

        name = classNameToTypeName(word);

        char c = currentChar();
        if (c == '<') {
            index++;
            params = parseParams();
            if (params == null) return null;
        }
        return new JavaType.TypeBound(name, params);
    }

    private JavaType.TypeParam nextTypeParam() {
        List<JavaType.TypeBound> bounds = List.of();
        List<JavaType.TypeParam> params = List.of();

        var name = nextWord(true);
        if (name.isEmpty()) {
            if (throwOnError) throw new RuntimeException("expected type parameter but got '" + currentChar() + "'");
            return null;
        }

        if (currentChar() == '<') {
            index++;
            params = parseParams();
            if (params == null) return null;
        }

        if (expectNext(" extends ")) {
            bounds = parseBounds(false);
            if (bounds == null) return null;
        } else {
            name = classNameToTypeName(name);
        }

        return new JavaType.TypeParam(name, bounds, params);
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
        if (throwOnError) throw new RuntimeException("expected type parameters to end with '>' but got '" +
                currentChar() + "'");

        return null;
    }

    private List<JavaType.TypeBound> parseBounds(boolean isInterfaces) {
        var bounds = new ArrayList<JavaType.TypeBound>(2);
        var keepGoing = true;
        while (keepGoing) {
            var bound = nextTypeBound();
            if (bound == null) return null;
            if (!JavaType.OBJECT.equals(bound)) {
                bounds.add(bound);
            }
            if (isInterfaces) {
                keepGoing = expectNext(",");
                expectNext(" "); // optional whitespace sometimes emitted by javap
            } else {
                keepGoing = expectNext(" & ");
            }
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
