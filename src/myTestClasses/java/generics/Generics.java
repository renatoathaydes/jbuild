package generics;

import foo.SomethingSpecific;

import java.util.function.Function;

public class Generics<T extends Base> {

    public String takeT(T t) {
        return t.string();
    }

    public <V, Z extends SomethingSpecific> void genericMethod(Function<? super V, ? extends Z> fun) {
    }

}
