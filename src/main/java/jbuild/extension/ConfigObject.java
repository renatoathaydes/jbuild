package jbuild.extension;

import jbuild.api.JBuildException;
import jbuild.classes.model.ClassFile;
import jbuild.classes.model.attributes.MethodParameter;
import jbuild.classes.signature.JavaTypeSignature;
import jbuild.classes.signature.JavaTypeSignature.BaseType;
import jbuild.classes.signature.JavaTypeSignature.ReferenceTypeSignature.ArrayTypeSignature;
import jbuild.classes.signature.JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature;
import jbuild.classes.signature.MethodSignature;
import jbuild.classes.signature.SimpleClassTypeSignature;
import jbuild.classes.signature.SimpleClassTypeSignature.TypeArgument;
import jbuild.util.JavaTypeUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

final class ConfigObject {

    private static final ClassTypeSignature STRING =
            new ClassTypeSignature("java.lang", new SimpleClassTypeSignature("String"));

    private static final ArrayTypeSignature ARRAY_STRING = new ArrayTypeSignature((short) 1, STRING);

    private static final ClassTypeSignature LIST_STRING =
            new ClassTypeSignature("java.util", new SimpleClassTypeSignature("List", List.of(
                    new TypeArgument.Reference(STRING))));

    enum ConfigType {
        STRING, BOOLEAN, INT, FLOAT, LIST_OF_STRINGS, ARRAY_OF_STRINGS,
    }

    static final class ConfigObjectConstructor {
        final Map<String, ConfigType> parameters;

        public ConfigObjectConstructor(LinkedHashMap<String, ConfigType> parameters) {
            this.parameters = parameters;
        }
    }

    static final class ConfigObjectDescriptor {

        final List<ConfigObjectConstructor> constructors;

        public ConfigObjectDescriptor(List<ConfigObjectConstructor> constructors) {
            this.constructors = constructors;
        }
    }

    static ConfigObjectDescriptor describeConfigObject(ClassFile extension) {
        var className = JavaTypeUtils.typeNameToClassName(extension.getTypeName());
        var constructors = extension.getConstructors().stream().map(constructor -> {
            var params = extension.getMethodParameters(constructor);
            ensureParameterNamesAvailable(params, className);

            // prefer to use the generic type if it's available as we need the type parameters
            return extension.getSignatureAttribute(constructor)
                    .map(signature -> createConstructor(className, params, signature))
                    .orElseGet(() -> createConstructor(className, params,
                            extension.getMethodTypeDescriptor(constructor)));
        });
        return new ConfigObjectDescriptor(constructors.collect(toList()));
    }

    private static ConfigType toConfigType(JavaTypeSignature arg, String className, String name) {
        if (arg instanceof BaseType) {
            var argType = (BaseType) arg;
            if (argType == BaseType.Z) return ConfigType.BOOLEAN;
            if (argType == BaseType.I) return ConfigType.INT;
            if (argType == BaseType.F) return ConfigType.FLOAT;
        } else if (arg instanceof ClassTypeSignature) {
            var refType = (ClassTypeSignature) arg;
            if (STRING.equals(refType)) return ConfigType.STRING;
            if (LIST_STRING.equals(refType)) return ConfigType.LIST_OF_STRINGS;
        } else if (arg instanceof ArrayTypeSignature) {
            var arrayType = (ArrayTypeSignature) arg;
            if (ARRAY_STRING.equals(arrayType)) return ConfigType.ARRAY_OF_STRINGS;
        }
        throw new JBuildException("At class " + className + ", constructor parameter '" + name +
                "' has an unsupported type for jb extension (use String, String[], List<String> or a primitive type)",
                JBuildException.ErrorCause.USER_INPUT);
    }

    private static ConfigObjectConstructor createConstructor(
            String className, List<MethodParameter> params, MethodSignature genericType) {
        var paramTypes = new LinkedHashMap<String, ConfigType>();
        if (!genericType.typeParameters.isEmpty()) {
            throw new JBuildException("Constructor of class " + className +
                    " is generic, which is not allowed in a jb extension.",
                    JBuildException.ErrorCause.USER_INPUT);
        }
        var paramIndex = 0;
        for (var arg : genericType.arguments) {
            var param = params.get(paramIndex++);
            var type = toConfigType(arg, className, param.name);
            paramTypes.put(param.name, type);
        }
        return new ConfigObjectConstructor(paramTypes);
    }

    private static void ensureParameterNamesAvailable(List<MethodParameter> params, String className) {
        if (params.stream().anyMatch(it -> it.name.isEmpty())) {
            throw new JBuildException("Constructor of class " + className + " has unnamed parameters - " +
                    "make sure to compile the class with the javac -parameters option " +
                    "or use a Java record for the configuration object.",
                    JBuildException.ErrorCause.USER_INPUT);
        }
    }

}
