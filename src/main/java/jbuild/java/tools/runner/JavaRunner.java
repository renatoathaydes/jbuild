package jbuild.java.tools.runner;

import jbuild.errors.JBuildException;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static jbuild.errors.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;

public final class JavaRunner {

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
        var classMethods = Stream.concat(Stream.of(type.getDeclaredMethods()), Stream.of(type.getMethods()))
                .filter(m -> m.getName().equals(method) && m.getParameterCount() == args.length)
                .collect(toList());

        if (!classMethods.isEmpty()) {
            Object object;
            try {
                object = type.getConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                throw new JBuildException(e.toString(), USER_INPUT);
            }

            for (var classMethod : classMethods) {
                var acceptedTypes = classMethod.getParameterTypes();
                if (typesMatch(acceptedTypes, args)) try {
                    return classMethod.invoke(object, args);
                } catch (IllegalAccessException e) {
                    throw new JBuildException(e.toString(), ACTION_ERROR);
                } catch (InvocationTargetException e) {
                    var cause = e.getCause();
                    throw new RuntimeException(cause == null ? e : cause);
                }
            }
        }

        throw new JBuildException("Unable to find method that could be invoked with the provided arguments: " +
                Arrays.toString(args), USER_INPUT);
    }

    private static boolean typesMatch(Class<?>[] acceptedTypes, Object[] args) {
        for (var i = 0; i < acceptedTypes.length; i++) {
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

}
