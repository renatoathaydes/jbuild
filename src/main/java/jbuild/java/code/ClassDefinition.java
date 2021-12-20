package jbuild.java.code;

import java.util.List;
import java.util.Map;

public final class ClassDefinition {

    public final String className;
    public final Map<String, Code.Field> fields;
    public final Map<MethodDefinition, List<Code>> methods;

    public ClassDefinition(String className,
                           Map<String, Code.Field> fields,
                           Map<MethodDefinition, List<Code>> methods) {
        this.className = className;
        this.fields = fields;
        this.methods = methods;
    }

}
