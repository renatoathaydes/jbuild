package jbuild.classes.model.attributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AnnotationInfo {
    public final String typeDescriptor;
    public final List<ElementValuePair> elementValuePairs;

    public AnnotationInfo(String typeDescriptor, List<ElementValuePair> elementValuePairs) {
        this.typeDescriptor = typeDescriptor;
        this.elementValuePairs = elementValuePairs;
    }

    public Map<String, ElementValuePair> getMap() {
        var map = new LinkedHashMap<String, ElementValuePair>(elementValuePairs.size());
        for (var pair : elementValuePairs) {
            map.put(pair.name, pair);
        }
        return map;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AnnotationInfo that = (AnnotationInfo) o;

        if (!typeDescriptor.equals(that.typeDescriptor)) return false;
        return elementValuePairs.equals(that.elementValuePairs);
    }

    @Override
    public int hashCode() {
        int result = typeDescriptor.hashCode();
        result = 31 * result + elementValuePairs.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "AnnotationInfo{" +
                "typeDescriptor='" + typeDescriptor + '\'' +
                ", elementValuePairs=" + elementValuePairs +
                '}';
    }
}
