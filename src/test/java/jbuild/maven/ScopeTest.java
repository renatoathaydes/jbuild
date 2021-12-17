package jbuild.maven;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

public class ScopeTest {

    @Test
    void scopeSetCanBeExpanded() {
        var set = EnumSet.noneOf(Scope.class);
        assertThat(Scope.expandScopes(set)).isEmpty();

        set = EnumSet.of(Scope.COMPILE);
        assertThat(Scope.expandScopes(set))
                .isEqualTo(EnumSet.of(Scope.COMPILE));

        set = EnumSet.of(Scope.RUNTIME);
        assertThat(Scope.expandScopes(set))
                .isEqualTo(EnumSet.of(Scope.COMPILE, Scope.RUNTIME));

        set = EnumSet.of(Scope.TEST);
        assertThat(Scope.expandScopes(set))
                .isEqualTo(EnumSet.of(Scope.TEST, Scope.COMPILE, Scope.RUNTIME));

        set = EnumSet.of(Scope.PROVIDED, Scope.IMPORT, Scope.SYSTEM);
        assertThat(Scope.expandScopes(set))
                .isEqualTo(EnumSet.of(Scope.PROVIDED, Scope.IMPORT, Scope.SYSTEM));
    }
}
