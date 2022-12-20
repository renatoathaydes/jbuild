package jbuild.java.code;

import java.util.List;
import java.util.Map;

public final class AnnotationValues {
    public final String name;
    private final Map<String, Object> values;

    public AnnotationValues(String name, Map<String, Object> values) {
        this.name = name;
        this.values = values;
    }

    public String getString(String key) {
        return (String) values.get(key);
    }

    public int getInt(String key) {
        return Math.toIntExact(((Double) values.get(key)).longValue());
    }

    public AnnotationValues getSub(String key) {
        return (AnnotationValues) values.get(key);
    }

    @SuppressWarnings("unchecked")
    public List<String> getAllStrings(String key) {
        return (List<String>) values.get(key);
    }
}
