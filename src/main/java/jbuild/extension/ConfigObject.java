package jbuild.extension;

import jbuild.api.JBuildException;
import jbuild.api.JBuildLogger;
import jbuild.api.config.JbConfig;
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

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

final class ConfigObject {

    private static final ClassTypeSignature STRING =
            new ClassTypeSignature("java/lang", new SimpleClassTypeSignature("", "String"));

    private static final ClassTypeSignature JBUILD_LOGGER =
            new ClassTypeSignature("jbuild/api", new SimpleClassTypeSignature("", "JBuildLogger"));

    private static final ClassTypeSignature JB_CONF =
            new ClassTypeSignature("jbuild/api/config", new SimpleClassTypeSignature("", "JbConfig"));

    private static final ArrayTypeSignature ARRAY_STRING = new ArrayTypeSignature((short) 1, STRING);

    private static final ClassTypeSignature LIST_STRING =
            new ClassTypeSignature("java/util", new SimpleClassTypeSignature("", "List", List.of(
                    new TypeArgument.Reference(STRING))));

    private static abstract class StringListTypeToken implements List<String> {
    }

    enum ConfigType {
        JBUILD_LOGGER(JBuildLogger.class),
        JB_CONFIG(JbConfig.class),
        STRING(String.class),
        BOOLEAN(boolean.class),
        INT(int.class),
        FLOAT(float.class),
        LIST_OF_STRINGS(StringListTypeToken.class.getGenericInterfaces()[0]),
        ARRAY_OF_STRINGS(String[].class),
        ;
        public final Type javaType;

        ConfigType(Type javaType) {
            this.javaType = javaType;
        }

        public static List<Type> getAllTypes() {
            return Arrays.stream(ConfigType.values())
                    .map(t -> t.javaType)
                    .collect(Collectors.toList());
        }
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
                    .map(signature -> createConstructor(className, params, (MethodSignature) signature))
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
            if (JBUILD_LOGGER.equals(refType)) return ConfigType.JBUILD_LOGGER;
            if (JB_CONF.equals(refType)) return ConfigType.JB_CONFIG;
        } else if (arg instanceof ArrayTypeSignature) {
            var arrayType = (ArrayTypeSignature) arg;
            if (ARRAY_STRING.equals(arrayType)) return ConfigType.ARRAY_OF_STRINGS;
        }

        var supportedTypes = ConfigType.getAllTypes().stream()
                .map(Type::getTypeName)
                .collect(Collectors.joining(", "));

        throw new JBuildException("At class " + className + ", constructor parameter '" + name +
                "' has an unsupported type for jb extension (use one of: " + supportedTypes + ")",
                JBuildException.ErrorCause.USER_INPUT);
    }

    private static ConfigObjectConstructor createConstructor(
            String className,
            List<MethodParameter> params,
            MethodSignature genericType) {
        var paramTypes = new LinkedHashMap<String, ConfigType>();
        if (!genericType.typeParameters.isEmpty()) {
            throw new JBuildException("Constructor of class " + className +
                    " is generic, which is not allowed in a jb extension.",
                    JBuildException.ErrorCause.USER_INPUT);
        }
        var paramIndex = 0;
        for (var arg : genericType.arguments) {
            var param = params.get(paramIndex++);
            paramTypes.put(param.name, toConfigType(arg, className, param.name));
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
