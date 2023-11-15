package jbuild.classes;

import jbuild.classes.signature.ClassSignature;
import jbuild.classes.signature.JavaTypeSignature;
import jbuild.classes.signature.JavaTypeSignature.ReferenceTypeSignature.ArrayTypeSignature;
import jbuild.classes.signature.JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature;
import jbuild.classes.signature.JavaTypeSignature.ReferenceTypeSignature.TypeVariableSignature;
import jbuild.classes.signature.MethodSignature;
import jbuild.classes.signature.SimpleClassTypeSignature;
import jbuild.classes.signature.TypeParameter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Parser of Java type signature descriptors as defined in
 * <a href="https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.7.9.1">JavaTypeSignature</a>.
 */
public final class JavaTypeSignatureParser {

    public static final class JavaTypeParseException extends RuntimeException {
        public final int index;
        public final String typeSignature;
        public final String errorMessage;

        public JavaTypeParseException(int index, String typeSignature, String errorMessage) {
            this.index = index;
            this.typeSignature = typeSignature;
            this.errorMessage = errorMessage;
        }

        @Override
        public String toString() {
            return errorMessage + '\n' + typeSignature + '\n' +
                    " ".repeat(Math.max(0, index)) +
                    '^';
        }
    }

    private static class State {
        int index;
    }

    private final State state = new State();

    /**
     * Parse a {@link JavaTypeSignature}.
     *
     * @param typeSignature Java type signature
     * @return the type signature
     */
    public JavaTypeSignature parseTypeSignature(String typeSignature) {
        state.index = 0;
        return parseTypeSignature(typeSignature, 0);
    }

    /**
     * Parse a {@link ClassSignature}.
     *
     * @param typeSignature class signature
     * @return the class signature
     */
    public ClassSignature parseClassSignature(String typeSignature) {
        state.index = 0;
        var typeParameters = parseTypeParameters(typeSignature);
        ensureCharHere('L', typeSignature, "SuperclassSignature");
        var superClass = parseClassTypeSignature(typeSignature);
        final var len = typeSignature.length();
        var superInterfaces = new ArrayList<ClassTypeSignature>(2);
        while (state.index < len) {
            ensureCharHere('L', typeSignature, "SuperinterfaceSignature");
            superInterfaces.add(parseClassTypeSignature(typeSignature));
        }
        return new ClassSignature(typeParameters, superClass, superInterfaces);
    }

    /**
     * Parse a {@link MethodSignature}.
     *
     * @param typeSignature Method signature descriptor
     * @return the method signature
     */
    public MethodSignature parseMethodSignature(String typeSignature) {
        state.index = 0;
        var typeParameters = parseTypeParameters(typeSignature);
        ensureCharHere('(', typeSignature, "MethodSignature type parameters");
        var methodArgs = new ArrayList<JavaTypeSignature>(4);
        while (next(typeSignature, false, () ->
                "expected to find end of MethodSignature but reached the end of the type") != ')') {
            methodArgs.add(parseTypeSignature(typeSignature, 0));
        }
        state.index++;
        var methodResult = parseMethodResult(typeSignature);
        var throwsSignature = parseThrowsSignature(typeSignature);
        return new MethodSignature(typeParameters, methodArgs, methodResult, throwsSignature);
    }

    private JavaTypeSignature parseTypeSignature(String typeSignature, int arrayDimensions) {
        var ch = next(typeSignature, false, () -> "expected TypeSignature but reached the end of the type");
        var baseType = JavaTypeSignature.BaseType.pickOrNull(ch);
        if (baseType != null) {
            state.index++;
            return maybeAsArray(baseType, arrayDimensions);
        }
        return parseReferenceTypeSignature(typeSignature, arrayDimensions);
    }

    private JavaTypeSignature.ReferenceTypeSignature parseReferenceTypeSignature(String typeSignature, int arrayDimensions) {
        var ch = next(typeSignature, () -> "expected ReferenceTypeSignature but reached the end of the type");
        switch (ch) {
            case 'L':
                return maybeAsArray(
                        parseClassTypeSignature(typeSignature),
                        arrayDimensions);
            case 'T':
                return maybeAsArray(
                        parseTypeVariableSignature(typeSignature),
                        arrayDimensions);
            case '[':
                return (ArrayTypeSignature) parseTypeSignature(typeSignature, arrayDimensions + 1);
            default:
                throw new JavaTypeParseException(state.index - 1, typeSignature,
                        "expected ReferenceTypeSignature but got '" + ch + "'");
        }
    }

    private ClassTypeSignature parseClassTypeSignature(String typeSignature) {
        var packages = new ArrayList<String>(4);
        String nextPart;
        while (true) {
            nextPart = parseIdentifier(typeSignature);
            var end = next(typeSignature, () ->
                    "expected PackageSpecifier or SimpleClassTypeSignature but reached the end of the type");
            if (end == '/') packages.add(nextPart);
            else {
                state.index--;
                break;
            }
        }
        var packageName = String.join(".", packages);
        var simpleTypeSignature = parseSimpleClassType(typeSignature, nextPart);
        var end = next(typeSignature, () ->
                "expected ';' after SimpleClassTypeSignature but reached the end of the type");
        switch (end) {
            case ';':
                return new ClassTypeSignature(packageName, simpleTypeSignature);
            case '.': {
                var typeSignatureSuffix = parseClassTypeSignatureSuffix(typeSignature);
                return new ClassTypeSignature(packageName, simpleTypeSignature, typeSignatureSuffix);
            }
            default:
                throw new JavaTypeParseException(state.index, typeSignature,
                        "unexpected character '" + end + "' after SimpleClassTypeSignature");
        }
    }

    private SimpleClassTypeSignature parseSimpleClassType(String typeSignature, String identifier) {
        if (state.index < typeSignature.length()) {
            var ch = typeSignature.charAt(state.index);
            if (ch == '<') {
                state.index++;
                var typeArgs = parseTypeArguments(typeSignature);
                return new SimpleClassTypeSignature(identifier, typeArgs);
            }
        }
        return new SimpleClassTypeSignature(identifier);
    }

    private List<SimpleClassTypeSignature> parseClassTypeSignatureSuffix(String typeSignature) {
        var result = new ArrayList<SimpleClassTypeSignature>(2);
        while (state.index < typeSignature.length()) {
            var identifier = parseIdentifier(typeSignature);
            result.add(parseSimpleClassType(typeSignature, identifier));
            var end = next(typeSignature, () ->
                    "expected end of ClassTypeSignatureSuffix but reached the end of the type");
            if (end == ';') return result;
            if (end != '.') {
                throw new JavaTypeParseException(state.index - 1, typeSignature,
                        "unexpected character '" + end + "' after SimpleClassTypeSignature");
            }
        }
        throw new JavaTypeParseException(state.index, typeSignature,
                "expected end of SimpleClassTypeSignature but reached the end of the type");
    }

    private List<SimpleClassTypeSignature.TypeArgument> parseTypeArguments(String typeSignature) {
        var result = new ArrayList<SimpleClassTypeSignature.TypeArgument>(2);
        while (true) {
            result.add(parseTypeArgument(typeSignature));
            var ch = next(typeSignature, () ->
                    "expected end of type arguments but reached the end of the type");
            if (ch == '>') return result;
            state.index--;
        }
    }

    private SimpleClassTypeSignature.TypeArgument parseTypeArgument(String typeSignature) {
        var ch = next(typeSignature, () -> "expected type argument but reached the end of the type");
        switch (ch) {
            case '*':
                return SimpleClassTypeSignature.TypeArgument.Star.INSTANCE;
            case '+':
                return new SimpleClassTypeSignature.TypeArgument.Reference(
                        parseReferenceTypeSignature(typeSignature, 0),
                        SimpleClassTypeSignature.WildCardIndicator.PLUS);
            case '-':
                return new SimpleClassTypeSignature.TypeArgument.Reference(
                        parseReferenceTypeSignature(typeSignature, 0),
                        SimpleClassTypeSignature.WildCardIndicator.MINUS);
            default:
                state.index--;
                return new SimpleClassTypeSignature.TypeArgument.Reference(
                        parseReferenceTypeSignature(typeSignature, 0));
        }
    }

    private TypeVariableSignature parseTypeVariableSignature(String typeSignature) {
        var identifier = parseIdentifier(typeSignature);
        var end = next(typeSignature, () ->
                "expected identifier ending at ';' but reached the end of the type");
        if (end != ';') {
            throw new JavaTypeParseException(state.index - 1, typeSignature, "invalid character '" + end +
                    "' after TypeVariableSignature identifier");
        }
        return new TypeVariableSignature(identifier);
    }

    private List<TypeParameter> parseTypeParameters(String typeSignature) {
        var ch = next(typeSignature, false, () -> "expected TypeParameters or SuperclassSignature " +
                "but reached the end the type");
        if (ch != '<') return List.of();
        state.index++;
        var typeParameter = parseTypeParameter(typeSignature);
        var result = new ArrayList<TypeParameter>(2);
        result.add(typeParameter);
        ch = next(typeSignature, false, () -> "expected TypeParameter '>' but reached the end the type");
        while (ch != '>') {
            result.add(parseTypeParameter(typeSignature));
            ch = next(typeSignature, false, () -> "expected TypeParameter '>' but reached the end the type");
        }
        state.index++;
        return result;
    }

    private TypeParameter parseTypeParameter(String typeSignature) {
        var identifier = parseIdentifier(typeSignature);
        ensureCharHere(':', typeSignature, "TypeParameter ClassBound");
        var beforeClassBound = next(typeSignature, false, () ->
                "expected ClassBound but reached the end the type");
        switch (beforeClassBound) {
            case ':': {
                var interfaceBounds = parseInterfaceBounds(typeSignature);
                return new TypeParameter(identifier, null, interfaceBounds);
            }
            case '>':
                return new TypeParameter(identifier, null, List.of());
            default: {
                var classBound = parseReferenceTypeSignature(typeSignature, 0);
                var interfaceBounds = parseInterfaceBounds(typeSignature);
                return new TypeParameter(identifier, classBound, interfaceBounds);
            }
        }
    }

    private List<JavaTypeSignature.ReferenceTypeSignature> parseInterfaceBounds(String typeSignature) {
        var ch = next(typeSignature, false, () -> "expected InterfaceBound but reached the end of the type");
        if (ch == '>') return List.of();
        var result = new ArrayList<JavaTypeSignature.ReferenceTypeSignature>(2);
        while (ch == ':') {
            state.index++;
            result.add(parseReferenceTypeSignature(typeSignature, 0));
            ch = next(typeSignature, false, () ->
                    "expected InterfaceBound or '>' but reached the end of the type");
        }
        return result;
    }

    private MethodSignature.MethodResult parseMethodResult(String typeSignature) {
        var ch = next(typeSignature, false, () -> "expected MethodResult but reached the end of the type");
        if (ch == 'V') {
            state.index++;
            return MethodSignature.MethodResult.VoidDescriptor.INSTANCE;
        }
        return new MethodSignature.MethodResult.MethodReturnType(parseTypeSignature(typeSignature, 0));
    }

    private List<MethodSignature.ThrowsSignature> parseThrowsSignature(String typeSignature) {
        final var len = typeSignature.length();
        if (state.index >= len) return List.of();
        var result = new ArrayList<MethodSignature.ThrowsSignature>(2);
        while (state.index < len) {
            var ch = next(typeSignature, () -> "expected ThrowsSignature but reached the end of the type");
            if (ch != '^') {
                throw new JavaTypeParseException(state.index - 1, typeSignature,
                        "expected ThrowsSignature but got '" + ch + "'");
            }
            ch = next(typeSignature, () -> "expected ClassTypeSignature or TypeVariableSignature of " +
                    "ThrowsSignature but reached the end of the type");
            switch (ch) {
                case 'L':
                    result.add(new MethodSignature.ThrowsSignature.Class(parseClassTypeSignature(typeSignature)));
                    break;
                case 'T':
                    result.add(new MethodSignature.ThrowsSignature.TypeVariable(parseTypeVariableSignature(typeSignature)));
                    break;
                default:
                    throw new JavaTypeParseException(state.index - 1, typeSignature, "expected ClassTypeSignature or " +
                            "TypeVariableSignature of ThrowsSignature but got '" + ch + "'");
            }
        }
        return result;
    }

    /**
     * Parse an identifier. Stops in the first invalid identifier character or end of input.
     * <p>
     * The grammar includes the terminal symbol Identifier to denote the name of a type, field, method,
     * formal parameter, local variable, or type variable, as generated by a Java compiler.
     * <p>
     * Such a name must not contain any of the ASCII characters {@code . ; [ / < > :}
     * (that is, the characters forbidden in method names (§4.2.2) and also colon)
     * but may contain characters that must not appear in an identifier in the Java programming language
     * (JLS §3.8).
     *
     * @param typeSignature being parsed
     * @return the identifier
     */
    private String parseIdentifier(String typeSignature) {
        var index = state.index;
        final var start = index;
        final var len = typeSignature.length();
        while (index < len) {
            var ch = typeSignature.charAt(index);
            if (ch == '.' || ch == ';' || ch == '[' || ch == '/' || ch == '<' || ch == '>' || ch == ':')
                break;
            index++;
        }
        var result = typeSignature.substring(start, index);
        if (result.isEmpty()) {
            throw new JavaTypeParseException(index, typeSignature, "expected identifier but got empty string");
        }
        state.index = index;
        return result;
    }

    private void ensureCharHere(char ch, String typeSignature, String expectation) {
        var here = next(typeSignature, () -> "expected '" + ch + "' (" + expectation +
                ") but reached the end of the type");
        if (here != ch) {
            throw new JavaTypeParseException(state.index, typeSignature,
                    "expected '" + ch + "' (" + expectation + ") but found '" + here + "' instead");
        }
    }

    private char next(String typeSignature, Supplier<String> errorIfEOF) {
        return next(typeSignature, true, errorIfEOF);
    }

    private char next(String typeSignature, boolean consume, Supplier<String> errorIfEOF) {
        var index = state.index;
        if (index < typeSignature.length()) {
            var ch = typeSignature.charAt(index);
            if (consume) state.index++;
            return ch;
        }
        throw new JavaTypeParseException(index, typeSignature, errorIfEOF.get());
    }

    private static JavaTypeSignature maybeAsArray(JavaTypeSignature result, int arrayDimensions) {
        if (arrayDimensions == 0) return result;
        return new ArrayTypeSignature((short) arrayDimensions, result);
    }

    private static JavaTypeSignature.ReferenceTypeSignature maybeAsArray(
            JavaTypeSignature.ReferenceTypeSignature result,
            int arrayDimensions) {
        if (arrayDimensions == 0) return result;
        return new ArrayTypeSignature((short) arrayDimensions, result);
    }

}
