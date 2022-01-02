package jbuild.java;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static jbuild.util.CollectionUtils.countRemaining;
import static jbuild.util.CollectionUtils.difference;
import static jbuild.util.CollectionUtils.mapValues;

public final class JarSet {

    private final Map<String, Set<String>> jarsByType;

    public JarSet(Map<String, Set<String>> jarsByType) {
        this.jarsByType = jarsByType;
    }

    /**
     * Compute the unique permutations of jars in this JarSet by which all types will belong to a single jar only,
     * returning a {@link Map} from each type to the single jar where it will be found.
     *
     * @return map from types to each unique jar where the type is found
     */
    public Set<Map<String, String>> computeUniqueJarSetPermutations() {
        var jarsWithDuplicatedTypes = jarsByType.values().stream()
                .filter(jars -> jars.size() > 1)
                .collect(toSet());

        if (jarsWithDuplicatedTypes.isEmpty()) {
            return Set.of(mapValues(jarsByType, jars -> jars.iterator().next()));
        }

        return computeUniqueJarSetPermutations(jarsWithDuplicatedTypes);
    }

    private Set<Map<String, String>> computeUniqueJarSetPermutations(Set<Set<String>> jarsWithDuplicatedTypes) {
        var result = new HashSet<Map<String, String>>();

        for (var jars : jarsWithDuplicatedTypes) {
            for (var jar : jars) {
                // remove all other jars and see if that results in a good permutation where all types are unique
                var jarsToRemove = difference(jars, Set.of(jar));
                var good = jarsByType.entrySet().stream()
                        .noneMatch(entry -> countRemaining(entry.getValue(), jarsToRemove) > 1L);
                if (good) {
                    result.add(jarsByType.entrySet().stream()
                            .filter(entry -> countRemaining(entry.getValue(), jarsToRemove) == 1L)
                            .collect(toMap(
                                    Map.Entry::getKey,
                                    e -> difference(e.getValue(), jarsToRemove).iterator().next())));
                }
            }
        }

        return result;
    }

}
