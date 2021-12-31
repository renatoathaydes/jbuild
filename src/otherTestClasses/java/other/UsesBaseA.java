package other;

import generics.BaseA;

public class UsesBaseA {
    BaseA baseA;

    void usesSuperMethod() {
        baseA.string();
    }

    void usesBaseAMethod() {
        baseA.aBoolean();
    }
}
