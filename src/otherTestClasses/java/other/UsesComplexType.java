package other;

import foo.EmptyInterface;
import foo.Zort;
import generics.BaseA;
import generics.ComplexType;
import generics.Generics;
import generics.ManyGenerics;

public class UsesComplexType {
    ComplexType<Param> complexType;
    public Zort[][][] zorts;

    Zort[][][] zorts(ManyGenerics<Zort, Generics<?>, String[], boolean[][]> manyGenerics) {
        zorts = complexType.zorts;
        return zorts;
    }

    public static final class Param extends Generics<BaseA> implements EmptyInterface {
    }
}
