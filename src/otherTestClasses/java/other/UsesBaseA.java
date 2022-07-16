package other;

import generics.BaseA;

public class UsesBaseA {
    BaseA baseA;

    @SuppressWarnings("AccessStaticViaInstance")
    void usesSuperMethod() {
        // invoke static method via instance
        baseA.aStaticMethod();

        baseA.string();
    }

    void usesBaseAMethod() {
        baseA.aBoolean();
    }
}
