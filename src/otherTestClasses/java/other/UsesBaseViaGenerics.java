package other;

import generics.Base;

public class UsesBaseViaGenerics<B extends Base> {
    B base;

    void useBase() {
        base.string();
    }
}
