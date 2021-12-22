package jbuild.java.code;

import java.util.Map;
import java.util.Set;

public final class ClassDefinition {

    public final String className;
    public final Set<FieldDefinition> fields;
    public final Map<MethodDefinition, Set<Code>> methods;

    public ClassDefinition(String className,
                           Set<FieldDefinition> fields,
                           Map<MethodDefinition, Set<Code>> methods) {
        this.className = className;
        this.fields = fields;
        this.methods = methods;
    }

}
