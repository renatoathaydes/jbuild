package jbuild.extension.runner;

import jbuild.api.JBuildException;
import jbuild.api.JBuildException.ErrorCause;
import jbuild.api.JBuildLogger;
import jbuild.api.change.ChangeSet;
import jbuild.api.config.JbConfig;
import jbuild.cli.RpcMain;
import jbuild.log.JBuildLog;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static jbuild.api.JBuildException.ErrorCause.ACTION_ERROR;
import static jbuild.api.JBuildException.ErrorCause.USER_INPUT;
import static jbuild.util.JavaTypeUtils.typeNameToClassName;

public final class JavaRunner {

    private enum ParamMatch {
        exact, varargsExact, varargsMissing, varargsExtra, no,
    }


    private static final class MethodMatch {
        final ParamMatch paramMatch;
        final Method method;

        static final MethodMatch NO = new MethodMatch(ParamMatch.no, null);

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

    private final URL[] classpath;
    private final JBuildLog log;

    public JavaRunner(String classpath, JBuildLog log) {
        this.log = log;
        if (classpath.isBlank()) {
            this.classpath = new URL[0];
        } else {
            var parts = classpath.split(File.pathSeparator);
            this.classpath = new URL[parts.length];
            for (var i = 0; i < parts.length; i++) {
                var file = new File(parts[i]);
                try {
                    this.classpath[i] = file.toURI().toURL();
                } catch (MalformedURLException e) {
                    throw new JBuildException("Invalid classpath URL: " + parts[i], USER_INPUT);
                }
            }
        }
    }

    public Object run(RpcMethodCall methodCall) {
        return run(methodCall.getClassName(), new Object[0], methodCall.getMethodName(), methodCall.getParameters());
    }

    public Object run(String className,
                      Object[] constructorData,
                      String method, Object... args) {
        Class<?> type;
        if (className == null || className.isBlank()) { // use RcpMain
            type = RpcMain.class;
            constructorData = new Object[1];
            constructorData[0] = log;
        } else try {
            type = loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new JBuildException("Class " + className + " does not exist", USER_INPUT);
        }
        var nameMethodMatches = Stream.of(type.getMethods())
                .filter(m -> !Modifier.isStatic(m.getModifiers()) && m.getName().equals(method))
                .collect(toList());

        var matchMethod = nameMethodMatches.stream()
                .map(m -> createMethodMatch(matchByCount(m, args.length), m))
                .sorted(Comparator.comparing(m -> m.paramMatch))
                .filter(m -> typesMatch(m, args))
                .findFirst();

        if (matchMethod.isPresent()) {
            log.verbosePrintln(() -> "Selected method to call with args " +
                    Arrays.deepToString(args) + ": " + matchMethod.get().method);

            Object object;
            try {
                object = createReceiverType(type, constructorData);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                throw new JBuildException(e.toString(), USER_INPUT);
            }

            final var out = System.out;
            System.setOut(log.getPrintStream());
            try {
                return invoke(object, matchMethod.get(), args);
            } catch (IllegalAccessException e) {
                throw new JBuildException(e.toString(), ACTION_ERROR);
            } catch (InvocationTargetException e) {
                var cause = e.getCause();
                throw new RuntimeException(cause == null ? e : cause);
            } finally {
                System.setOut(out);
            }
        }

        String reason;
        if (nameMethodMatches.isEmpty()) {
            reason = "No method called '" + method + "' found in class " + type.getName();
        } else {
            reason = "Expected parameters for method with name '" + method + "' in class " + type.getName() +
                    ":\n" + nameMethodMatches.stream()
                    .map(m -> Stream.of(m.getParameters())
                            .map(Objects::toString)
                            .collect(joining(", ")))
                    .collect(joining("\n  * ", "  * ", "\n"));
        }

        throw new JBuildException("Unable to find method that could be invoked with the provided arguments: " +
                Arrays.deepToString(args) + ".\nProvided parameter types:\n  * " +
                typesOf(args) + "\n" + reason, USER_INPUT);
    }

    private MethodMatch createMethodMatch(ParamMatch paramMatch, Method method) {
        if (paramMatch == ParamMatch.no) {
            return MethodMatch.NO;
        }
        return new MethodMatch(paramMatch, method);
    }

    private Object createReceiverType(Class<?> type, Object... constructorData)
            throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Constructor<?> constructor = Arrays.stream(type.getConstructors())
                .filter(c -> typesMatch(c, constructorData))
                .findFirst()
                .orElse(null);
        if (constructor != null) {
            return constructor.newInstance(populateConstructorData(constructor.getParameters(), constructorData));
        }

        return type.getConstructor().newInstance();
    }

    private Object[] populateConstructorData(Parameter[] parameters, Object... constructorData) {
        for (int i = 0; i < parameters.length; i++) {
            var arg = constructorData[i];
            var paramType = parameters[i].getType();
            if (paramType.equals(JBuildLogger.class)) {
                if (arg == null) {
                    constructorData[i] = log;
                } else if (!(arg instanceof JBuildLogger)) {
                    throw new JBuildException("Constructor parameter of type JBuildLogger was provided a " +
                            "non-null value", ACTION_ERROR);
                }
            } else if (paramType.equals(JbConfig.class)) {
                // TODO support JbConfig
                throw new UnsupportedOperationException("Cannot support JbConfig yet");
            } else if (paramType.isArray()) {
                constructorData[i] = fixArrayArgs(new Class[]{paramType}, new Object[]{arg})[0];
            }
        }
        return constructorData;
    }

    private Class<?> loadClass(String className) throws ClassNotFoundException {
        if (classpath.length == 0) {
            return Class.forName(className);
        }
        try (var classLoader = new URLClassLoader(classpath, JavaRunner.class.getClassLoader())) {
            return classLoader.loadClass(className);
        } catch (IOException e) {
            throw new JBuildException(
                    "Error creating ClassLoader from classpath: " + Arrays.toString(classpath),
                    ErrorCause.IO_READ);
        }
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
            } else if (acceptedType.isArray()) {
                if (!arrayTypesMatch(acceptedType, args[i])) return false;
            } else if (acceptedType.equals(ChangeSet.class)) {
                return args[i] instanceof ChangeSet;
            } else if (args[i] != null && !acceptedType.isInstance(args[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean typesMatch(Constructor<?> constructor, Object[] args) {
        var acceptedTypes = constructor.getParameterTypes();
        if (acceptedTypes.length != args.length) return false;
        for (var i = 0; i < args.length; i++) {
            var acceptedType = acceptedTypes[i];
            if (acceptedType.isPrimitive()) {
                if (!typesMatchPrimitive(acceptedType, args[i])) {
                    return false;
                }
            } else if (acceptedType.isArray()) {
                if (!arrayTypesMatch(acceptedType, args[i])) return false;
            } else if (args[i] != null && !acceptedType.isInstance(args[i])) {
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

    private static boolean arrayTypesMatch(Class<?> arrayType, Object arg) {
        if (arg == null) return false;
        if (arg instanceof List<?>) {
            return arrayTypesMatchList(arrayType, (List<?>) arg);
        }
        if (!arg.getClass().isArray()) return false;

        if (arrayType.isInstance(arg)) return true;
        var componentType = arrayType.getComponentType();
        for (var i = 0; i < Array.getLength(arg); i++) {
            var element = Array.get(arg, i);
            if (element != null && !componentType.isInstance(element)) return false;
        }
        return true;
    }

    private static boolean arrayTypesMatchList(Class<?> arrayType, List<?> arg) {
        if (arg.isEmpty()) return true;
        var componentType = arrayType.getComponentType();
        return componentType.isInstance(arg.get(0));
    }

    private String typesOf(Object[] args) {
        return Stream.of(args).map(a -> a == null ? "<null>" : a.getClass().getName())
                .collect(joining(", "));
    }

    private Object invoke(Object object, MethodMatch match, Object... args)
            throws IllegalAccessException, InvocationTargetException {
        Object[] fixedArgs;
        switch (match.paramMatch) {
            case exact:
                fixedArgs = fixArrayArgs(match.method.getParameterTypes(), args);
                break;
            case varargsExact:
                var lastArg = args[args.length - 1];
                if (lastArg.getClass().isArray()) {
                    fixedArgs = fixArrayArgs(match.method.getParameterTypes(), args);
                } else {
                    // make the last arg an array, so it matches the varargs parameter
                    fixedArgs = withLastArgAsArray(args, lastArg);
                }
                break;
            case varargsMissing:
                // make the last arg an empty array, so it matches the varargs parameter
                fixedArgs = addEmptyArrayAtEnd(match.method.getParameterTypes(), args);
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

    private static Object[] addEmptyArrayAtEnd(Class<?>[] parameterTypes, Object[] args) {
        var varargsArg = convertArrayType(parameterTypes[parameterTypes.length - 1].getComponentType(),
                new Object[0]);
        var result = new Object[args.length + 1];
        result[result.length - 1] = varargsArg;
        System.arraycopy(args, 0, result, 0, args.length);
        return result;
    }

    private Object[] fixArrayArgs(Class<?>[] parameterTypes, Object[] args) {
        assert parameterTypes.length == args.length;
        for (var i = 0; i < args.length; i++) {
            var paramType = parameterTypes[i];
            var arg = args[i];
            if (paramType.isArray()) {
                if (arg != null) {
                    if (arg.getClass().isArray()) {
                        if (!paramType.isInstance(arg)) {
                            args[i] = convertArrayType(paramType.getComponentType(), arg);
                        }
                    } else if (arg instanceof List<?>) {
                        args[i] = convertListType(paramType.getComponentType(), (List<?>) arg);
                    }
                }
            } else if (arg == null) {
                if (paramType.equals(JBuildLogger.class)) {
                    args[i] = log;
                }
            }
        }
        return args;
    }

    private static Object convertListType(Class<?> componentType, List<?> arg) {
        var array = Array.newInstance(componentType, arg.size());
        for (int i = 0; i < arg.size(); i++) {
            try {
                Array.set(array, i, arg.get(i));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("array of type " + componentType.getName() +
                        " cannot accept item: " + arg.get(i));
            }
        }
        return array;
    }

    private static Object convertArrayType(Class<?> componentType, Object arg) {
        var length = Array.getLength(arg);
        var array = Array.newInstance(componentType, length);
        for (var i = 0; i < length; i++) {
            try {
                Array.set(array, i, arg);
            } catch (IllegalArgumentException e) {
                throw new JBuildException("Cannot set element " + i + " of array with type " +
                        componentType.getName() + "[] to '" + Arrays.deepToString((Object[]) arg) + "' of type " +
                        typeNameToClassName(arg == null ? "Null" : arg.getClass().getName()) +
                        ": " + e,
                        ACTION_ERROR);
            }
        }
        return array;
    }

}
