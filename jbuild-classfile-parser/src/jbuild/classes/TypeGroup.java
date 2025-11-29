package jbuild.classes;

import java.util.Set;

/**
 * An Object that may contain or group Java types.
 */
public interface TypeGroup {

    /**
     * @return all individual Java type names included in this type group.
     */
    Set<String> getAllTypes();
}
