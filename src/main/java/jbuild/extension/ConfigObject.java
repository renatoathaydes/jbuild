package jbuild.extension;

import jbuild.api.JBuildException;
import jbuild.classes.model.ClassFile;
import jbuild.classes.model.attributes.AnnotationInfo;
import jbuild.classes.model.attributes.MethodParameter;
import jbuild.classes.signature.JavaTypeSignature;
import jbuild.classes.signature.JavaTypeSignature.BaseType;
import jbuild.classes.signature.JavaTypeSignature.ReferenceTypeSignature.ArrayTypeSignature;
import jbuild.classes.signature.JavaTypeSignature.ReferenceTypeSignature.ClassTypeSignature;
import jbuild.classes.signature.MethodSignature;
import jbuild.classes.signature.SimpleClassTypeSignature;
import jbuild.classes.signature.SimpleClassTypeSignature.TypeArgument;
import jbuild.util.JavaTypeUtils;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

final class ConfigObject {

    private static final ClassTypeSignature STRING =
            new ClassTypeSignature("java.lang", new SimpleClassTypeSignature("String"));

    private static final ClassTypeSignature JBUILD_LOGGER =
            new ClassTypeSignature("jbuild.api", new SimpleClassTypeSignature("JBuildLogger"));

    private static final ArrayTypeSignature ARRAY_STRING = new ArrayTypeSignature((short) 1, STRING);

    private static final ClassTypeSignature LIST_STRING =
            new ClassTypeSignature("java.util", new SimpleClassTypeSignature("List", List.of(
                    new TypeArgument.Reference(STRING))));

    enum ConfigType {
        STRING, BOOLEAN, INT, FLOAT, LIST_OF_STRINGS, ARRAY_OF_STRINGS, JBUILD_LOGGER
    }

    static final class ConstructorArgument {
        final ConfigType type;
        /**
         * The JBuild config name if the parameter is annotated with {@link jbuild.api.JbConfigProperty},
         * or the empty String otherwise.
         */
        final String jbuildConfigName;

        public ConstructorArgument(ConfigType type, String jbuildConfigName) {
            this.type = type;
            this.jbuildConfigName = jbuildConfigName;
        }
    }

    static final class ConfigObjectConstructor {
        final Map<String, ConstructorArgument> parameters;

        public ConfigObjectConstructor(LinkedHashMap<String, ConstructorArgument> parameters) {
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
            var annotations = extension.getRuntimeInvisibleParameterAnnotations(constructor).iterator();

            // prefer to use the generic type if it's available as we need the type parameters
            return extension.getSignatureAttribute(constructor)
                    .map(signature -> createConstructor(className, params, signature, annotations))
                    .orElseGet(() -> createConstructor(className, params,
                            extension.getMethodTypeDescriptor(constructor), annotations));
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
        } else if (arg instanceof ArrayTypeSignature) {
            var arrayType = (ArrayTypeSignature) arg;
            if (ARRAY_STRING.equals(arrayType)) return ConfigType.ARRAY_OF_STRINGS;
        }
        throw new JBuildException("At class " + className + ", constructor parameter '" + name +
                "' has an unsupported type for jb extension (use String, String[], List<String>, JBuildLogger " +
                "or primitive types boolean, int or float)",
                JBuildException.ErrorCause.USER_INPUT);
    }

    private static ConfigObjectConstructor createConstructor(
            String className,
            List<MethodParameter> params,
            MethodSignature genericType,
            Iterator<List<AnnotationInfo>> annotationsPerParameter) {
        var paramTypes = new LinkedHashMap<String, ConstructorArgument>();
        if (!genericType.typeParameters.isEmpty()) {
            throw new JBuildException("Constructor of class " + className +
                    " is generic, which is not allowed in a jb extension.",
                    JBuildException.ErrorCause.USER_INPUT);
        }
        var paramIndex = 0;
        for (var arg : genericType.arguments) {
            var param = params.get(paramIndex++);
            var annotations = annotationsPerParameter.hasNext()
                    ? annotationsPerParameter.next()
                    : List.<AnnotationInfo>of();
            var jbuildConfigName = extractJBuildConfigName(annotations, param.name);
            var type = toConfigType(arg, className, param.name);
            paramTypes.put(param.name, new ConstructorArgument(type, jbuildConfigName));
        }
        return new ConfigObjectConstructor(paramTypes);
    }

    private static String extractJBuildConfigName(List<AnnotationInfo> annotations, String name) {
        return annotations.stream().filter(a -> a.typeDescriptor.equals("Ljbuild/api/JbConfigProperty;"))
                .findFirst()
                .map(a -> a.elementValuePairs.isEmpty()
                        ? name
                        : (String) a.elementValuePairs.get(0).value)
                .orElse("");
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
