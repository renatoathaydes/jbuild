package jbuild.java;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static jbuild.util.CollectionUtils.append;
import static jbuild.util.JavaTypeUtils.classNameToTypeName;
import static jbuild.util.TextUtils.trimEnd;

/**
 * A parser that can identify and parse a type identifier line given {@link Tools.Javap}'s output.
 * <p>
 * This class helps {@link JavapOutputParser} to find types in the javap tool's output.
 * <p>
 * Because it is in the "hot path" when parsing javap output, this class was designed to be fast when given lots
 * of invalid lines to parse... for this reason, instead of throwing Exceptions or returning errors, when created with
 * {@code throwOnError} set to {@code false}, it will simply
 * return {@code null} as soon as it finds out that a particular line is not a valid type identifier line.
 * <p>
 * This class is mutable and not Thread-safe.
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
        var typeId = parseTypeId(line);
        if (typeId == null) return null;
        return parseType(typeId);
    }

    public JavaType parseSignature(JavaType.TypeId typeId, String line) {
        this.line = line;
        this.index = 0;
        return parseSignature(typeId);
    }

    public JavaType.TypeId parseTypeId(String typeIdLine) {
        this.line = typeIdLine;
        this.index = 0;

        JavaType.TypeId typeId = null;

        while (index >= 0 && index < line.length()) {
            var word = nextWord();
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
                var kind = JavaType.Kind.valueOf(word.toUpperCase(Locale.ROOT));
                var spec = nextTypeBound(false, List.of());
                if (spec == null) return null;
                var name = spec.name;
                typeId = new JavaType.TypeId(name, kind);
                break;
            } else {
                if (throwOnError) throw new RuntimeException("expected a type kind or modifier but got '" + word + "'");
                return null;
            }
        }

        if (typeId == null || typeId.name.isBlank()) {
            if (throwOnError) throw new RuntimeException("no type name found");
            return null;
        }

        return typeId;
    }

    private JavaType parseType(JavaType.TypeId typeId) {
        List<JavaType.TypeBound> superTypes = List.of();
        List<JavaType.TypeBound> interfaces = List.of();

        if (index >= 0 && index < line.length()) {
            if (expectNext(" extends ")) {
                if (typeId.kind == JavaType.Kind.INTERFACE) {
                    interfaces = parseInterfaces();
                    if (interfaces == null) return null;
                } else {
                    var bound = nextTypeBound(false, List.of());
                    if (bound == null) return null;
                    if (!bound.equals(JavaType.OBJECT)) {
                        superTypes = List.of(bound);
                    }
                }
            }
        }

        if (typeId.kind != JavaType.Kind.INTERFACE &&
                (expectNext(" implements ") || expectNext("implements "))) {
            interfaces = parseInterfaces();
            if (interfaces == null) {
                return null;
            }
        }
        if (index < line.length()) {
            if (throwOnError)
                throw new RuntimeException("unexpected input following type at index " + index);
            return null;
        }

        return new JavaType(typeId, superTypes, List.of(), interfaces);
    }

    private JavaType parseSignature(JavaType.TypeId typeId) {
        List<JavaType.TypeBound> interfaces = List.of();
        List<JavaType.TypeParam> typeParameters = List.of();

        if (currentChar() == '<') {
            index++;
            typeParameters = parseTypeSignatureParameters(typeParameters);
            if (typeParameters == null) return null;
        }

        var bound = nextTypeBound(true, typeParameters);
        if (bound == null) return null;
        var superTypes = JavaType.OBJECT.equals(bound)
                ? List.<JavaType.TypeBound>of()
                : List.of(bound);

        if (index < line.length()) {
            interfaces = new ArrayList<>(2);
            while (index < line.length()) {
                bound = nextTypeBound(true, typeParameters);
                if (bound == null) return null;
                interfaces.add(bound);
            }
        }

        if (superTypes.stream().anyMatch(t -> t.name.equals("Ljava/lang/Enum;"))) {
            typeId = new JavaType.TypeId(typeId.name, JavaType.Kind.ENUM);
        }

        return new JavaType(typeId, superTypes, typeParameters, interfaces);
    }

    private List<JavaType.TypeParam> parseTypeSignatureParameters(
            Iterable<JavaType.TypeParam> previousParameters) {
        var params = new ArrayList<JavaType.TypeParam>(2);
        var c = currentChar();
        while (c != '>' && index < line.length()) {
            var param = nextTypeParameter(append(params, previousParameters));
            if (param == null) return null;
            params.add(param);
            c = currentChar();
        }
        if (c == '>') index++;
        return params;
    }

    private JavaType.TypeParam nextTypeParameter(Iterable<JavaType.TypeParam> previousParameters) {
        String name;
        var bounds = new ArrayList<JavaType.TypeBound>(2);
        var params = List.<JavaType.TypeParam>of();

        var word = nextWord();
        if (word.isEmpty()) {
            if (throwOnError) throw new RuntimeException("expected type parameter but got '" +
                    currentChar() + "' at index " + index);
            return null;
        }
        if (word.startsWith("+") || word.startsWith("-")) {
            name = word.substring(1);
        } else {
            name = word;
        }
        var isTypeParameter = !name.startsWith("L") && !name.startsWith("[");
        var c = currentChar();
        if (c == ':') {
            isTypeParameter = true;
            while (c == ':') {
                index++;

                // sometimes, a double ':' shows up, just ignore the second one
                if (currentChar() == ':') index++;

                var bound = nextTypeBound(true, previousParameters);
                if (bound == null) return null;
                if (!JavaType.OBJECT.equals(bound)) {
                    bounds.add(bound);
                }
                c = currentChar();
            }
        } else if (c == '<') {
            index++;
            params = parseTypeSignatureParameters(previousParameters);
            if (params == null) return null;
            if (currentChar() == ';') index++;
        } else if (c == ';') {
            index++;
        }

        if (!isTypeParameter) {
            name += ';';
        }

        return new JavaType.TypeParam(name, bounds, params);
    }

    private JavaType.TypeBound nextTypeBound(boolean isTypeSignature,
                                             Iterable<JavaType.TypeParam> previousParams) {
        String name;
        List<JavaType.TypeParam> params = List.of();

        var word = nextWord();
        if (word.isEmpty()) {
            if (throwOnError) throw new RuntimeException("expected type bound but got '" +
                    currentChar() + "' at index " + index);
            return null;
        }

        if (isTypeSignature) {
            if (currentChar() == ';') {
                index++;
                name = isParameter(previousParams, word) ? word : word + ';';
            } else if (currentChar() == '<') {
                index++;
                name = word + ';';
                params = parseTypeSignatureParameters(previousParams);
                if (params == null) return null;
                if (currentChar() == ';') index++;
                else while (currentChar() == '.') {
                    index++;
                    var nestedBound = nextTypeBound(true,
                            append(previousParams, params));
                    if (nestedBound == null) return null;
                    name = trimEnd(name, ';') + '$' + nestedBound.name.replaceAll("\\.", "\\$");
                    params = nestedBound.params;
                }
            } else if (throwOnError) {
                throw new RuntimeException("expected end of type bound ';' but got '" +
                        currentChar() + "' at index " + index);
            } else {
                return null;
            }
        } else {
            name = classNameToTypeName(word);
        }

        return new JavaType.TypeBound(name, params);
    }

    private static boolean isParameter(Iterable<JavaType.TypeParam> typeParameters, String word) {
        if (!word.contains("/") && word.startsWith("T")) {
            var paramName = word.substring(1);
            for (var param : typeParameters) {
                if (param.name.equals(paramName)) return true;
            }
        }
        return false;
    }

    private List<JavaType.TypeBound> parseInterfaces() {
        var bounds = new ArrayList<JavaType.TypeBound>(2);
        var keepGoing = true;
        while (keepGoing) {
            var bound = nextTypeBound(false, List.of());
            if (bound == null) return null;
            if (!JavaType.OBJECT.equals(bound)) {
                bounds.add(bound);
            }
            keepGoing = expectNext(",");
            expectNext(" "); // optional whitespace sometimes emitted by javap
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

    private String nextWord() {
        var next = nextSeparator();
        if (index == next) {
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
            if ((c < '.' && c != '$' && c != '+' && c != '-' && c != '*')
                    || c > 'z'
                    || (c > '9' && c < 'A')
                    || (c > '[' && c < 'a' && c != ']' && c != '_')) return i;
        }
        return i;
    }

}
