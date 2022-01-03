package generics;

import foo.EmptyInterface;
import foo.Zort;

import java.util.concurrent.Callable;
import java.util.function.Function;

public abstract class ComplexType<T extends Generics<? extends BaseA> & EmptyInterface>
        extends Generics<Base>
        implements Callable<Generics<BaseA>>, Runnable, Function<String, Generics<Base>> {
    String[] strings;
    long[][] longs;
    public Zort[][][] zorts;
}
