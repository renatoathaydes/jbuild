package jbuild.java.tools.runner;

import jbuild.errors.JBuildException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;

public final class JavaRunner {

    private enum ParamMatch {
        exact, varargsExact, varargsMissing, varargsExtra, no,
    }

    private static class MethodMatch {
        final ParamMatch paramMatch;
        final Method method;

        public MethodMatch(ParamMatch paramMatch, Method method) {
            this.paramMatch = paramMatch;
            this.method = method;
        }

        public int checkedParamsCount() {
            switch (paramMatch) {
                case exact:
                    return method.getParameterCount();
                case varargsExtra:
                case varargsExact:
                case varargsMissing:
                    // the last parameter will be converted to an array if needed
                    return method.getParameterCount() - 1;
                case no:
                default:
                    throw new IllegalStateException();
            }
        }
    }

    public Object run(RpcMethodCall methodCall) {
        return run(methodCall.getClassName(), methodCall.getMethodName(), methodCall.getParameters());
    }

    public Object run(String className, String method, Object... args) {
        Class<?> type;
        try {
            type = Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new JBuildException("Class " + className + " does not exist", USER_INPUT);
        }
        var matchMethod = Stream.concat(Stream.of(type.getDeclaredMethods()), Stream.of(type.getMethods()))
                .filter(m -> m.getName().equals(method))
                .map(m -> new MethodMatch(matchByCount(m, args.length), m))
                .filter(m -> typesMatch(m, args))
                .findFirst();

        if (matchMethod.isPresent()) {
            Object object;
            try {
                object = type.getConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                throw new JBuildException(e.toString(), USER_INPUT);
            }

            try {
                return invoke(object, matchMethod.get(), args);
            } catch (IllegalAccessException e) {
                throw new JBuildException(e.toString(), ACTION_ERROR);
            } catch (InvocationTargetException e) {
                var cause = e.getCause();
                throw new RuntimeException(cause == null ? e : cause);
            }
        }

        throw new JBuildException("Unable to find method that could be invoked with the provided arguments: " +
                Arrays.toString(args) + " (" + typesOf(args) + ")", USER_INPUT);
    }

    private static ParamMatch matchByCount(Method method, int argsCount) {
        var count = method.getParameterCount();
        var isVarargs = count > 0 && method.getParameters()[count - 1].isVarArgs();
        if (count == argsCount) return isVarargs ? ParamMatch.varargsExact : ParamMatch.exact;
        if (isVarargs) {
            var diff = argsCount - count;
            if (diff == -1) return ParamMatch.varargsMissing;
            if (diff > 0) return ParamMatch.varargsExtra;
        }
        return ParamMatch.no;
    }

    private static boolean typesMatch(MethodMatch match, Object[] args) {
        if (match.paramMatch == ParamMatch.no) return false;
        var acceptedTypes = match.method.getParameterTypes();
        for (var i = 0; i < match.checkedParamsCount(); i++) {
            var acceptedType = acceptedTypes[i];
            if (acceptedType.isPrimitive()) {
                if (!typesMatchPrimitive(acceptedType, args[i])) {
                    return false;
                }
            } else if (!acceptedType.isInstance(args[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean typesMatchPrimitive(Class<?> primitiveType, Object arg) {
        if (primitiveType.equals(int.class)) {
            return arg instanceof Integer;
        }
        if (primitiveType.equals(double.class)) {
            return arg instanceof Double;
        }
        if (primitiveType.equals(boolean.class)) {
            return arg instanceof Boolean;
        }
        return false;
    }

    private String typesOf(Object[] args) {
        return Stream.of(args).map(a -> a == null ? "<null>" : a.getClass().getName())
                .collect(joining(", "));
    }

    private static Object invoke(Object object, MethodMatch match, Object... args)
            throws IllegalAccessException, InvocationTargetException {
        Object[] fixedArgs;
        switch (match.paramMatch) {
            case exact:
                return match.method.invoke(object, args);
            case varargsExact:
                var lastArg = args[args.length - 1];
                if (lastArg.getClass().isArray()) {
                    return match.method.invoke(object, args);
                }
                // make the last arg an array, so it matches the varargs parameter
                fixedArgs = withLastArgAsArray(args, lastArg);
                break;
            case varargsMissing:
                // make the last arg an empty array, so it matches the varargs parameter
                fixedArgs = addEmptyArrayAtEnd(args);
                break;
            case varargsExtra:
                // extra args need to go in the last parameter array
                fixedArgs = fitExtraArgsInto(args, match.method.getParameterCount());
                break;
            case no:
            default:
                throw new IllegalStateException();
        }

        return match.method.invoke(object, fixedArgs);
    }

    private static Object[] fitExtraArgsInto(Object[] args, int parameterCount) {
        var lastParam = new Object[args.length - parameterCount + 1];
        var result = new Object[parameterCount];
        result[parameterCount - 1] = lastParam;
        System.arraycopy(args, 0, result, 0, parameterCount - 1);
        System.arraycopy(args, parameterCount, lastParam, 0, lastParam.length - 1);
        return result;

    }

    private static Object[] withLastArgAsArray(Object[] args, Object lastArg) {
        args[args.length - 1] = new Object[]{lastArg};
        return args;
    }

    private static Object[] addEmptyArrayAtEnd(Object[] args) {
        var result = new Object[args.length + 1];
        result[result.length - 1] = new Object[0];
        System.arraycopy(args, 0, result, 0, args.length);
        return result;
    }

}
