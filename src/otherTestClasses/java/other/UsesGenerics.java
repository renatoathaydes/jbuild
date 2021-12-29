package other;

import generics.BaseA;
import generics.Generics;

public class UsesGenerics {

    Generics<BaseA> generics;

    void baseA() {
        generics.takeT(new BaseA());
    }

}
