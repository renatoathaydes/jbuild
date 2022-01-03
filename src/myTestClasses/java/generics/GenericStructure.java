package generics;

public class GenericStructure<D> {
    class Data<D> {
        D data;
    }

    class OtherData extends GenericStructure<D>.Data<D> {

    }
}
